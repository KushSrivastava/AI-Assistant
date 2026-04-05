package com.knowledgebot.ai.generation;

import org.springframework.stereotype.Service;
import java.util.Map;

@Service
public class PromptTemplateRegistry {

    private static final Map<String, String> TEMPLATES = Map.of(
        "java", "You are an expert Java developer. Use Java 21+ features like Records, pattern matching and structured concurrency. Follow Spring Boot best practices.",
        "python", "You are an expert Python developer. Ensure strict PEP-8 compliance and use type hints.",
        "typescript", "You are an expert TypeScript developer. Use strict typing and modern functional patterns.",
        "sql", "You are an expert SQL DBA. Prioritize standard, secure ANSI SQL."
    );

    public String getSystemPromptForLanguage(String language) {
        return TEMPLATES.getOrDefault(language.toLowerCase(), "You are an expert developer assistant.");
    }
}
