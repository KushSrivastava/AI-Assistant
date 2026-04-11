package com.knowledgebot.core.performance;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

@Service
public class TokenBudgetService {
    private static final Logger log = LoggerFactory.getLogger(TokenBudgetService.class);

    private final AtomicLong sessionTokens = new AtomicLong(0);
    private volatile long sessionBudget = 50000;
    private volatile boolean circuitOpen = false;
    private final HardwareMetricsService hardwareMetrics;
    private static final int DEFAULT_MAX_TOKENS = 32000;

    public TokenBudgetService(HardwareMetricsService hardwareMetrics) {
        this.hardwareMetrics = hardwareMetrics;
    }

    public int getAllocatedContextWindow() {
        if (hardwareMetrics.isRamCritical()) {
            // Save memory during intensive operations
            return DEFAULT_MAX_TOKENS / 2;
        }
        return DEFAULT_MAX_TOKENS;
    }

    public long estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        return (long) (text.length() / 3.5);
    }

    public boolean isWithinBudget(String text, long maxTokens) {
        long estimated = estimateTokens(text);
        if (estimated > maxTokens) {
            log.warn("Payload size ({} tokens) exceeds budget limit ({} tokens). Truncation may occur.", estimated, maxTokens);
            return false;
        }
        return true;
    }

    public void addTokens(long tokens) {
        long total = sessionTokens.addAndGet(tokens);
        if (sessionBudget > 0 && total > sessionBudget) {
            circuitOpen = true;
            log.error("CIRCUIT BREAKER OPEN: Session consumed {} tokens, exceeding budget of {} tokens. Task must terminate.", total, sessionBudget);
        }
    }

    public void resetSession() {
        sessionTokens.set(0);
        circuitOpen = false;
    }

    public void setSessionBudget(long budget) {
        this.sessionBudget = budget;
    }

    public boolean isCircuitOpen() {
        return circuitOpen;
    }

    public long getSessionTokens() {
        return sessionTokens.get();
    }

    public long getRemainingBudget() {
        if (sessionBudget <= 0) return Long.MAX_VALUE;
        return Math.max(0, sessionBudget - sessionTokens.get());
    }

    public void assertBudgetAvailable() {
        if (circuitOpen) {
            throw new TokenBudgetExceededException(
                String.format("Token budget exhausted. Consumed %d of %d allocated tokens.", sessionTokens.get(), sessionBudget));
        }
    }

    public String extractMethodSignatures(String fullSource) {
        if (fullSource == null || fullSource.isEmpty()) return "";
        try {
            JavaParser javaParser = new JavaParser();
            CompilationUnit cu = javaParser.parse(fullSource).getResult().orElse(null);
            if (cu == null) return "[Error: Parsing failed]";

            StringBuilder sb = new StringBuilder();
            cu.getTypes().forEach(type -> {
                sb.append("\nClass: ").append(type.getNameAsString()).append("\n");
                type.getMethods().forEach(method -> {
                    sb.append("  - ").append(method.getDeclarationAsString()).append("\n");
                });
            });
            return sb.toString();
        } catch (Exception e) {
            log.error("Failed to extract signatures: {}", e.getMessage());
            return "[Error: Signature extraction failed]";
        }
    }

    public static class TokenBudgetExceededException extends RuntimeException {
        public TokenBudgetExceededException(String message) {
            super(message);
        }
    }
}
