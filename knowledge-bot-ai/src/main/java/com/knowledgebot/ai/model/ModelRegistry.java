package com.knowledgebot.ai.model;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class ModelRegistry {

    @Value("${knowledge-bot.model-routing.default-model-key:local-fast}")
    private String defaultModelKey;

    @Value("${knowledge-bot.model-routing.fallback-model-key:local-fallback}")
    private String fallbackModelKey;

    @Value("${knowledge-bot.model-routing.auto-failover:true}")
    private boolean autoFailover;

    @Value("${knowledge-bot.model-routing.request-timeout-ms:30000}")
    private long requestTimeoutMs;

    @Value("${knowledge-bot.model-routing.local-fast-provider:OLLAMA}")
    private String localFastProvider;
    @Value("${knowledge-bot.model-routing.local-fast-model:llama3.2:3b}")
    private String localFastModel;
    @Value("${knowledge-bot.model-routing.local-fast-min:SIMPLE}")
    private String localFastMin;
    @Value("${knowledge-bot.model-routing.local-fast-max:SIMPLE}")
    private String localFastMax;
    @Value("${knowledge-bot.model-routing.local-fast-cost:0.0}")
    private double localFastCost;
    @Value("${knowledge-bot.model-routing.local-fast-tokens:32000}")
    private double localFastTokens;
    @Value("${knowledge-bot.model-routing.local-fast-purpose:Fast local responses}")
    private String localFastPurpose;
    @Value("${knowledge-bot.model-routing.local-fast-context:8192}")
    private int localFastContext;
    @Value("${knowledge-bot.model-routing.local-fast-budget:4000}")
    private int localFastBudget;

    @Value("${knowledge-bot.model-routing.local-code-provider:OLLAMA}")
    private String localCodeProvider;
    @Value("${knowledge-bot.model-routing.local-code-model:deepseek-coder-v2:16b}")
    private String localCodeModel;
    @Value("${knowledge-bot.model-routing.local-code-min:SIMPLE}")
    private String localCodeMin;
    @Value("${knowledge-bot.model-routing.local-code-max:MODERATE}")
    private String localCodeMax;
    @Value("${knowledge-bot.model-routing.local-code-cost:0.0}")
    private double localCodeCost;
    @Value("${knowledge-bot.model-routing.local-code-tokens:32000}")
    private double localCodeTokens;
    @Value("${knowledge-bot.model-routing.local-code-purpose:Code generation}")
    private String localCodePurpose;
    @Value("${knowledge-bot.model-routing.local-code-context:32768}")
    private int localCodeContext;
    @Value("${knowledge-bot.model-routing.local-code-budget:16000}")
    private int localCodeBudget;

    @Value("${knowledge-bot.model-routing.local-fallback-provider:OLLAMA}")
    private String localFallbackProvider;
    @Value("${knowledge-bot.model-routing.local-fallback-model:llama3.2:3b}")
    private String localFallbackModel;
    @Value("${knowledge-bot.model-routing.local-fallback-min:SIMPLE}")
    private String localFallbackMin;
    @Value("${knowledge-bot.model-routing.local-fallback-max:REASONING_HEAVY}")
    private String localFallbackMax;
    @Value("${knowledge-bot.model-routing.local-fallback-cost:0.0}")
    private double localFallbackCost;
    @Value("${knowledge-bot.model-routing.local-fallback-tokens:32000}")
    private double localFallbackTokens;
    @Value("${knowledge-bot.model-routing.local-fallback-purpose:Fallback model}")
    private String localFallbackPurpose;
    @Value("${knowledge-bot.model-routing.local-fallback-context:8192}")
    private int localFallbackContext;
    @Value("${knowledge-bot.model-routing.local-fallback-budget:4000}")
    private int localFallbackBudget;

    @Value("${knowledge-bot.model-routing.advanced-reasoning-provider:OLLAMA}")
    private String advancedReasoningProvider;
    @Value("${knowledge-bot.model-routing.advanced-reasoning-model:deepseek-coder-v2:16b}")
    private String advancedReasoningModel;
    @Value("${knowledge-bot.model-routing.advanced-reasoning-min:COMPLEX}")
    private String advancedReasoningMin;
    @Value("${knowledge-bot.model-routing.advanced-reasoning-max:REASONING_HEAVY}")
    private String advancedReasoningMax;
    @Value("${knowledge-bot.model-routing.advanced-reasoning-cost:0.0}")
    private double advancedReasoningCost;
    @Value("${knowledge-bot.model-routing.advanced-reasoning-tokens:32000}")
    private double advancedReasoningTokens;
    @Value("${knowledge-bot.model-routing.advanced-reasoning-purpose:Complex reasoning}")
    private String advancedReasoningPurpose;
    @Value("${knowledge-bot.model-routing.advanced-reasoning-context:32768}")
    private int advancedReasoningContext;
    @Value("${knowledge-bot.model-routing.advanced-reasoning-budget:16000}")
    private int advancedReasoningBudget;

    @Value("${knowledge-bot.model-routing.advanced-creative-provider:OLLAMA}")
    private String advancedCreativeProvider;
    @Value("${knowledge-bot.model-routing.advanced-creative-model:deepseek-coder-v2:16b}")
    private String advancedCreativeModel;
    @Value("${knowledge-bot.model-routing.advanced-creative-min:MODERATE}")
    private String advancedCreativeMin;
    @Value("${knowledge-bot.model-routing.advanced-creative-max:REASONING_HEAVY}")
    private String advancedCreativeMax;
    @Value("${knowledge-bot.model-routing.advanced-creative-cost:0.0}")
    private double advancedCreativeCost;
    @Value("${knowledge-bot.model-routing.advanced-creative-tokens:32000}")
    private double advancedCreativeTokens;
    @Value("${knowledge-bot.model-routing.advanced-creative-purpose:Creative tasks}")
    private String advancedCreativePurpose;
    @Value("${knowledge-bot.model-routing.advanced-creative-context:32768}")
    private int advancedCreativeContext;
    @Value("${knowledge-bot.model-routing.advanced-creative-budget:16000}")
    private int advancedCreativeBudget;

    private final Map<String, ModelDescriptor> models = new LinkedHashMap<>();

    @PostConstruct
    public void init() {
        models.put("local-fast", new ModelDescriptor(
            parseProvider(localFastProvider), localFastModel,
            TaskComplexity.valueOf(localFastMin), TaskComplexity.valueOf(localFastMax),
            localFastCost, localFastTokens, localFastContext, localFastBudget, localFastPurpose));
        models.put("local-code", new ModelDescriptor(
            parseProvider(localCodeProvider), localCodeModel,
            TaskComplexity.valueOf(localCodeMin), TaskComplexity.valueOf(localCodeMax),
            localCodeCost, localCodeTokens, localCodeContext, localCodeBudget, localCodePurpose));
        models.put("local-fallback", new ModelDescriptor(
            parseProvider(localFallbackProvider), localFallbackModel,
            TaskComplexity.valueOf(localFallbackMin), TaskComplexity.valueOf(localFallbackMax),
            localFallbackCost, localFallbackTokens, localFallbackContext, localFallbackBudget, localFallbackPurpose));
        models.put("advanced-reasoning", new ModelDescriptor(
            parseProvider(advancedReasoningProvider), advancedReasoningModel,
            TaskComplexity.valueOf(advancedReasoningMin), TaskComplexity.valueOf(advancedReasoningMax),
            advancedReasoningCost, advancedReasoningTokens, advancedReasoningContext, advancedReasoningBudget, advancedReasoningPurpose));
        models.put("advanced-creative", new ModelDescriptor(
            parseProvider(advancedCreativeProvider), advancedCreativeModel,
            TaskComplexity.valueOf(advancedCreativeMin), TaskComplexity.valueOf(advancedCreativeMax),
            advancedCreativeCost, advancedCreativeTokens, advancedCreativeContext, advancedCreativeBudget, advancedCreativePurpose));
    }

    public ModelDescriptor getModel(String key) {
        return models.get(key);
    }

    public ModelDescriptor getDefaultModel() {
        return models.getOrDefault(defaultModelKey, models.values().iterator().next());
    }

    public ModelDescriptor getFallbackModel() {
        return models.getOrDefault(fallbackModelKey, getDefaultModel());
    }

    /**
     * Returns the first model matching the given provider.
     * Falls back to the default model if no match is found.
     */
    public ModelDescriptor getModelByProvider(ModelProvider provider) {
        return models.values().stream()
                .filter(m -> m.provider() == provider)
                .findFirst()
                .orElseGet(this::getDefaultModel);
    }

    public List<ModelDescriptor> getAllModels() {
        return List.copyOf(models.values());
    }

    public boolean isAutoFailover() {
        return autoFailover;
    }

    public long getRequestTimeoutMs() {
        return requestTimeoutMs;
    }

    private ModelProvider parseProvider(String name) {
        try {
            return ModelProvider.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ModelProvider.OLLAMA;
        }
    }
}
