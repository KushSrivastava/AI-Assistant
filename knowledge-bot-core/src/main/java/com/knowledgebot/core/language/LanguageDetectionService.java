package com.knowledgebot.core.language;

import org.springframework.stereotype.Service;
import java.nio.file.Path;

@Service
public class LanguageDetectionService {
    public String detectLanguage(Path file) {
        String name = file.getFileName().toString();
        if (name.endsWith(".java")) return "java";
        if (name.endsWith(".py")) return "python";
        if (name.endsWith(".ts")) return "typescript";
        if (name.endsWith(".js")) return "javascript";
        if (name.endsWith(".sql")) return "sql";
        return "unknown";
    }
}
