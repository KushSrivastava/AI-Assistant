package com.knowledgebot.core.security;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OutputSanitizer {

    private static final List<String> DANGEROUS_PATTERNS = List.of(
        "Runtime.getRuntime().exec",
        "ProcessBuilder",
        "System.exit(",
        ".delete(",
        "Files.delete"
    );

    public String sanitize(String llmOutput) {
        for (String pattern : DANGEROUS_PATTERNS) {
            if (llmOutput.contains(pattern)) {
                return "\n[\u26A0\uFE0F DANGER - REVIEW CAREFULLY: The generated code contains potentially hazardous operational commands. Verify before executing!]\n\n" + llmOutput;
            }
        }
        return llmOutput;
    }
}
