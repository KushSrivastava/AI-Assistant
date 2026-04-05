package com.knowledgebot.core.security;

import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class PromptInjectionGuard {

    private static final List<String> INJECTION_PATTERNS = List.of(
        "ignore all previous instructions",
        "forget your previous instructions",
        "system prompt",
        "you are no longer"
    );

    public boolean isSafe(String userQuery) {
        String lowerQuery = userQuery.toLowerCase();
        for (String pattern : INJECTION_PATTERNS) {
            if (lowerQuery.contains(pattern)) {
                return false;
            }
        }
        return true;
    }
}
