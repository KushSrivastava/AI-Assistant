package com.knowledgebot.ai.model;

import java.util.Map;

public record ModelDescriptor(
    ModelProvider provider,
    String modelName,
    TaskComplexity minComplexity,
    TaskComplexity maxComplexity,
    double costPer1kTokens,
    double maxTokens,
    int maxContextTokens,
    int recommendedPromptBudget,
    String purpose
) {
    public boolean canHandle(TaskComplexity complexity) {
        return complexity.compareTo(minComplexity) >= 0 && complexity.compareTo(maxComplexity) <= 0;
    }

    public boolean isWithinContextBudget(long promptTokens) {
        return promptTokens <= maxContextTokens;
    }

    public boolean shouldPrune(long promptTokens) {
        return promptTokens > recommendedPromptBudget;
    }

    public static ModelDescriptor ollamaLocal(String model, TaskComplexity max, int contextWindow, int promptBudget, String purpose) {
        return new ModelDescriptor(ModelProvider.OLLAMA, model, TaskComplexity.SIMPLE, max, 0.0, contextWindow, contextWindow, promptBudget, purpose);
    }

    public static ModelDescriptor openaiCloud(String model, TaskComplexity min, int contextWindow, int promptBudget, String purpose) {
        return new ModelDescriptor(ModelProvider.OPENAI, model, min, TaskComplexity.REASONING_HEAVY, 0.01, contextWindow, contextWindow, promptBudget, purpose);
    }
}
