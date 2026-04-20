package com.knowledgebot.ai.model;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * WHY: Maintains a live registry of all configured LLM models.
 *
 * REFACTORED: Previously had 60+ individual @Value annotations (one per model field).
 * Now delegates entirely to {@link ModelRoutingProperties} which uses Spring Boot's
 * @ConfigurationProperties — a clean, scalable alternative that supports dynamic
 * maps and requires zero boilerplate per new model.
 *
 * HOW IT WORKS:
 *  1. Spring Boot reads "knowledge-bot.model-routing.models" from application.yml
 *  2. It maps each named entry to a ModelRoutingProperties.ModelConfig object
 *  3. @PostConstruct converts those configs into ModelDescriptor records
 *  4. Consumers (ModelRouterService etc.) call getDefaultModel() / getModel(key)
 */
@Configuration
public class ModelRegistry {

    private final ModelRoutingProperties properties;
    private final Map<String, ModelDescriptor> models = new LinkedHashMap<>();

    public ModelRegistry(ModelRoutingProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        models.clear();
        properties.getModels().forEach((key, cfg) ->
            models.put(key, toDescriptor(cfg))
        );
    }

    // ── Public API ────────────────────────────────────────────────────────

    public ModelDescriptor getModel(String key) {
        return models.get(key);
    }

    public ModelDescriptor getDefaultModel() {
        return models.getOrDefault(
            properties.getDefaultModelKey(),
            models.isEmpty() ? null : models.values().iterator().next()
        );
    }

    public ModelDescriptor getFallbackModel() {
        return models.getOrDefault(properties.getFallbackModelKey(), getDefaultModel());
    }

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
        return properties.isAutoFailover();
    }

    public long getRequestTimeoutMs() {
        return properties.getRequestTimeoutMs();
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private ModelDescriptor toDescriptor(ModelRoutingProperties.ModelConfig cfg) {
        return new ModelDescriptor(
            parseProvider(cfg.getProvider()),
            cfg.getModel(),
            parseComplexity(cfg.getMinComplexity()),
            parseComplexity(cfg.getMaxComplexity()),
            cfg.getCost(),
            cfg.getMaxTokens(),
            cfg.getContextWindow(),
            cfg.getPromptBudget(),
            cfg.getPurpose()
        );
    }

    private ModelProvider parseProvider(String name) {
        try {
            return ModelProvider.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ModelProvider.OLLAMA;
        }
    }

    private TaskComplexity parseComplexity(String name) {
        try {
            return TaskComplexity.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return TaskComplexity.SIMPLE;
        }
    }
}
