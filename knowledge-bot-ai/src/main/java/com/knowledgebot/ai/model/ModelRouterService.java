package com.knowledgebot.ai.model;

import com.knowledgebot.core.performance.TokenBudgetService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Adaptive model router.
 *
 * Routes tasks by task complexity (SIMPLE → local-fast, MODERATE → local-code,
 * COMPLEX → cloud), then within eligible candidates selects the lowest-score model:
 *
 *   score = 0.5 × normalisedLatency + 0.5 × normalisedCost + errorPenalty
 *
 * Latency is tracked via an Exponential Moving Average updated by {@link MetricsAdvisor}
 * after every real call. Scores are re-logged every 60 seconds.
 *
 * Inspired by OpenClaude's smart_router.py.
 */
@Service
public class ModelRouterService {

    private static final Logger log = LoggerFactory.getLogger(ModelRouterService.class);

    private static final long RE_EVAL_INTERVAL_SECONDS = 60;

    private final ModelRegistry modelRegistry;
    private final IntentClassifier intentClassifier;
    private final ChatClientFactory chatClientFactory;
    private final TokenBudgetService tokenBudgetService;

    /** Live metrics per model name — shared with ChatClientFactory so MetricsAdvisor writes here. */
    private final Map<String, ModelMetrics> metricsMap = new ConcurrentHashMap<>();

    private final AtomicReference<ModelDescriptor> lastUsedModel = new AtomicReference<>();

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "model-router-re-eval");
                t.setDaemon(true);
                return t;
            });

    public ModelRouterService(ModelRegistry modelRegistry,
                              IntentClassifier intentClassifier,
                              ChatClientFactory chatClientFactory,
                              TokenBudgetService tokenBudgetService) {
        this.modelRegistry = modelRegistry;
        this.intentClassifier = intentClassifier;
        this.chatClientFactory = chatClientFactory;
        this.tokenBudgetService = tokenBudgetService;
    }

    @PostConstruct
    void init() {
        // Seed a ModelMetrics entry for every registered model
        modelRegistry.getAllModels().forEach(d -> metricsMap.put(d.modelName(), new ModelMetrics()));
        // Share the metrics map with the factory so MetricsAdvisor can update it
        chatClientFactory.setMetricsMap(metricsMap);
        // Periodic health snapshot log
        scheduler.scheduleAtFixedRate(this::logHealthSnapshot,
                RE_EVAL_INTERVAL_SECONDS, RE_EVAL_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public ModelDescriptor selectModel(String prompt) {
        TaskComplexity complexity = intentClassifier.classify(prompt);
        ModelDescriptor selected = selectAdaptive(complexity);
        lastUsedModel.set(selected);
        log.info("Routed '{}' (complexity={}) → model:{} [{}]",
                abbreviated(prompt), complexity, selected.modelName(),
                metricsMap.getOrDefault(selected.modelName(), new ModelMetrics()));
        return selected;
    }

    public ChatClient getClientForPrompt(String prompt) {
        ModelDescriptor descriptor = selectModel(prompt);
        return chatClientFactory.getClientForModel(descriptor, modelRegistry);
    }

    public ChatClient getClientForComplexity(TaskComplexity complexity) {
        ModelDescriptor descriptor = selectAdaptive(complexity);
        lastUsedModel.set(descriptor);
        return chatClientFactory.getClientForModel(descriptor, modelRegistry);
    }

    public ChatClient getFallbackClient() {
        ModelDescriptor fallback = modelRegistry.getFallbackModel();
        log.warn("Falling back to model: {} ({})", fallback.modelName(), fallback.provider());
        return chatClientFactory.getClientForModel(fallback, modelRegistry);
    }

    public ModelDescriptor getLastUsedModel() {
        return lastUsedModel.get();
    }

    public int getActivePromptBudget() {
        ModelDescriptor model = lastUsedModel.get();
        if (model == null) model = modelRegistry.getDefaultModel();
        return model.recommendedPromptBudget();
    }

    public int getActiveContextLimit() {
        ModelDescriptor model = lastUsedModel.get();
        if (model == null) model = modelRegistry.getDefaultModel();
        return model.maxContextTokens();
    }

    public boolean shouldPruneForActiveModel(long promptTokens) {
        ModelDescriptor model = lastUsedModel.get();
        if (model == null) return false;
        return model.shouldPrune(promptTokens);
    }

    /** Formatted snapshot of current metrics — exposed via CLI `status` command. */
    public String getMetricsReport() {
        StringBuilder sb = new StringBuilder("=== Model Routing Metrics ===\n");
        for (ModelDescriptor d : modelRegistry.getAllModels()) {
            ModelMetrics m = metricsMap.getOrDefault(d.modelName(), new ModelMetrics());
            sb.append(String.format("  %-30s | %s%n", d.modelName(), m));
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private ModelDescriptor selectAdaptive(TaskComplexity complexity) {
        List<ModelDescriptor> candidates = modelRegistry.getAllModels().stream()
                .filter(d -> d.canHandle(complexity))
                .toList();

        if (candidates.isEmpty()) {
            log.warn("No candidate for complexity {}, using fallback", complexity);
            return modelRegistry.getFallbackModel();
        }
        if (candidates.size() == 1) {
            return candidates.get(0);
        }

        // Normalise across candidate pool so latency and cost are on the same scale
        double maxLatency = candidates.stream()
                .mapToDouble(d -> metricsMap.getOrDefault(d.modelName(), new ModelMetrics()).getAvgLatencyMs())
                .max().orElse(1.0);
        double maxCost = candidates.stream()
                .mapToDouble(ModelDescriptor::costPer1kTokens)
                .max().orElse(1.0);

        double latencyDiv = maxLatency == 0 ? 1.0 : maxLatency;
        double costDiv    = maxCost    == 0 ? 1.0 : maxCost;

        return candidates.stream()
                .min(Comparator.comparingDouble(d -> {
                    ModelMetrics m = metricsMap.getOrDefault(d.modelName(), new ModelMetrics());
                    return m.computeScore(m.getAvgLatencyMs() / latencyDiv,
                                         d.costPer1kTokens()  / costDiv);
                }))
                .orElse(candidates.get(0));
    }

    private void logHealthSnapshot() {
        log.info("Model health snapshot:\n{}", getMetricsReport());
    }

    private static String abbreviated(String text) {
        return text.length() > 60 ? text.substring(0, 60) + "…" : text;
    }
}
