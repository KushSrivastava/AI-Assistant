package com.knowledgebot.ai.model;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * WHY: Instead of 60+ @Value fields scattered across ModelRegistry, we bind the
 * entire YAML section "knowledge-bot.model-routing" to this single POJO.
 *
 * HOW IT WORKS: Spring Boot reads the YAML and automatically maps nested keys
 * to these fields. For example:
 *   knowledge-bot.model-routing.default-model-key: local-fast
 * gets mapped to this.defaultModelKey
 *
 * This replaces the flat, unscalable approach with a clean map-based structure.
 */
@ConfigurationProperties(prefix = "knowledge-bot.model-routing")
public class ModelRoutingProperties {

    private String defaultModelKey = "local-fast";
    private String fallbackModelKey = "local-fallback";
    private boolean autoFailover = true;
    private long requestTimeoutMs = 30000;

    /**
     * Each entry in the map represents one model configuration.
     * In YAML it looks like:
     *   knowledge-bot.model-routing.models:
     *     local-fast:
     *       provider: OLLAMA
     *       model: llama3.2:3b
     *       min-complexity: SIMPLE
     *       ...
     */
    private Map<String, ModelConfig> models = new LinkedHashMap<>();

    // ── Getters & Setters ──────────────────────────────────────────────────

    public String getDefaultModelKey() { return defaultModelKey; }
    public void setDefaultModelKey(String defaultModelKey) { this.defaultModelKey = defaultModelKey; }

    public String getFallbackModelKey() { return fallbackModelKey; }
    public void setFallbackModelKey(String fallbackModelKey) { this.fallbackModelKey = fallbackModelKey; }

    public boolean isAutoFailover() { return autoFailover; }
    public void setAutoFailover(boolean autoFailover) { this.autoFailover = autoFailover; }

    public long getRequestTimeoutMs() { return requestTimeoutMs; }
    public void setRequestTimeoutMs(long requestTimeoutMs) { this.requestTimeoutMs = requestTimeoutMs; }

    public Map<String, ModelConfig> getModels() { return models; }
    public void setModels(Map<String, ModelConfig> models) { this.models = models; }

    // ── Inner class: one model's configuration ─────────────────────────────

    public static class ModelConfig {
        private String provider = "OLLAMA";
        private String model;
        private String minComplexity = "SIMPLE";
        private String maxComplexity = "MODERATE";
        private double cost = 0.0;
        private double maxTokens = 32000;
        private int contextWindow = 8192;
        private int promptBudget = 4000;
        private String purpose = "";

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }

        public String getMinComplexity() { return minComplexity; }
        public void setMinComplexity(String minComplexity) { this.minComplexity = minComplexity; }

        public String getMaxComplexity() { return maxComplexity; }
        public void setMaxComplexity(String maxComplexity) { this.maxComplexity = maxComplexity; }

        public double getCost() { return cost; }
        public void setCost(double cost) { this.cost = cost; }

        public double getMaxTokens() { return maxTokens; }
        public void setMaxTokens(double maxTokens) { this.maxTokens = maxTokens; }

        public int getContextWindow() { return contextWindow; }
        public void setContextWindow(int contextWindow) { this.contextWindow = contextWindow; }

        public int getPromptBudget() { return promptBudget; }
        public void setPromptBudget(int promptBudget) { this.promptBudget = promptBudget; }

        public String getPurpose() { return purpose; }
        public void setPurpose(String purpose) { this.purpose = purpose; }
    }
}
