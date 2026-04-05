package com.knowledgebot.ai.model;

public enum ModelProvider {
    OLLAMA("ollama"),
    OPENAI("openai");

    private final String name;

    ModelProvider(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
