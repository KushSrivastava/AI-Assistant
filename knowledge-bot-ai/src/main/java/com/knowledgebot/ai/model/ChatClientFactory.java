package com.knowledgebot.ai.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds and caches per-model ChatClients using Ollama.
 * Each client is decorated with a {@link MetricsAdvisor} so that the
 * {@link ModelRouterService} receives live latency/error feedback.
 */
@Service
public class ChatClientFactory {

    private static final Logger log = LoggerFactory.getLogger(ChatClientFactory.class);

    private final OllamaChatModel ollamaChatModel;

    // modelName → ChatClient (cached after first build)
    private final Map<String, ChatClient> clientCache = new ConcurrentHashMap<>();

    // Injected by ModelRouterService after construction so we can register advisors
    private Map<String, ModelMetrics> metricsMap;

    public ChatClientFactory(OllamaChatModel ollamaChatModel) {
        this.ollamaChatModel = ollamaChatModel;
    }

    /** Called by ModelRouterService once after metrics map is initialised. */
    public void setMetricsMap(Map<String, ModelMetrics> metricsMap) {
        this.metricsMap = metricsMap;
    }

    public ChatClient getClientForModel(ModelDescriptor descriptor, ModelRegistry registry) {
        return clientCache.computeIfAbsent(descriptor.modelName(),
                key -> buildClientForDescriptor(descriptor));
    }

    public ChatClient getClient(ModelRegistry registry, ModelProvider provider) {
        ModelDescriptor descriptor = registry.getModelByProvider(provider);
        return clientCache.computeIfAbsent(descriptor.modelName(),
                key -> buildClientForDescriptor(descriptor));
    }

    public void invalidateCache() {
        clientCache.clear();
        log.info("ChatClient cache invalidated.");
    }

    private ChatClient buildClientForDescriptor(ModelDescriptor descriptor) {
        MetricsAdvisor advisor = buildAdvisor(descriptor.modelName());
        return ChatClient.builder(ollamaChatModel)
                .defaultOptions(OllamaChatOptions.builder()
                        .model(descriptor.modelName())
                        .temperature(0.2)
                        .build())
                .defaultAdvisors(advisor)
                .build();
    }

    private MetricsAdvisor buildAdvisor(String modelName) {
        ModelMetrics metrics = metricsMap != null
                ? metricsMap.computeIfAbsent(modelName, k -> new ModelMetrics())
                : new ModelMetrics();
        return new MetricsAdvisor(modelName, metrics);
    }
}
