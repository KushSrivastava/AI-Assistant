package com.knowledgebot.ai.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class IntentClassifier {

    private static final Logger log = LoggerFactory.getLogger(IntentClassifier.class);

    private static final Set<String> SIMPLE_KEYWORDS = Set.of(
        "summarize", "summary", "what is", "explain", "list", "count",
        "find", "search", "show", "read", "what does", "how many"
    );

    private static final Set<String> MODERATE_KEYWORDS = Set.of(
        "generate", "create", "convert", "migrate", "refactor", "modernize",
        "review", "analyze", "compare", "implement", "build", "write code"
    );

    private static final Set<String> COMPLEX_KEYWORDS = Set.of(
        "architecture", "design pattern", "multi-step", "pipeline", "orchestrate",
        "plan and implement", "full stack", "end to end", "deploy", "ci/cd"
    );

    private static final Set<String> REASONING_KEYWORDS = Set.of(
        "why does", "debug", "troubleshoot", "root cause", "optimize performance",
        "complex algorithm", "solve", "figure out", "reason about"
    );

    public TaskComplexity classify(String prompt) {
        String lower = prompt.toLowerCase();

        if (REASONING_KEYWORDS.stream().anyMatch(lower::contains)) {
            log.debug("Classified as REASONING_HEAVY: {}", prompt);
            return TaskComplexity.REASONING_HEAVY;
        }

        if (COMPLEX_KEYWORDS.stream().anyMatch(lower::contains)) {
            log.debug("Classified as COMPLEX: {}", prompt);
            return TaskComplexity.COMPLEX;
        }

        if (MODERATE_KEYWORDS.stream().anyMatch(lower::contains) || prompt.length() > 200) {
            log.debug("Classified as MODERATE: {}", prompt);
            return TaskComplexity.MODERATE;
        }

        if (SIMPLE_KEYWORDS.stream().anyMatch(lower::contains) || prompt.length() <= 100) {
            log.debug("Classified as SIMPLE: {}", prompt);
            return TaskComplexity.SIMPLE;
        }

        log.debug("Defaulting to MODERATE: {}", prompt);
        return TaskComplexity.MODERATE;
    }
}
