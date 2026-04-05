package com.knowledgebot.core.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Logic-adapted from the user's recommendations: Implements a Cost/Token Tracker.
 * Tracks usage per task even for local models (LLaVA/Qwen).
 */
@Component
public class CostTokenTracker {
    private static final Logger log = LoggerFactory.getLogger(CostTokenTracker.class);
    
    private final AtomicLong totalTokensProcessed = new AtomicLong(0);

    /**
     * Tracks tokens processed for a specific task.
     * @param tokens Number of tokens used
     */
    public void trackUsage(long tokens) {
        long currentTotal = totalTokensProcessed.addAndGet(tokens);
        log.info("Task usage tracked: {} tokens. Cumulative total: {} tokens.", tokens, currentTotal);
        
        // Hypothetical cost calculation for local resource usage / simulated cloud cost
        double simulatedCost = (tokens / 1000.0) * 0.0002; 
        log.debug("Simulated cost for this task: ${}", String.format("%.4f", simulatedCost));
    }

    public long getTotalTokens() {
        return totalTokensProcessed.get();
    }
}
