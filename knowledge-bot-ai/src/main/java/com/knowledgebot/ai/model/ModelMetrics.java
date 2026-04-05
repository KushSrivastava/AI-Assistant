package com.knowledgebot.ai.model;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runtime performance metrics per model, updated after every call.
 * Used by ModelRouterService for adaptive weighted-score routing.
 *
 * Score formula (lower is better):
 *   score = 0.5 × normalisedLatency + 0.5 × normalisedCost + errorPenalty
 */
public class ModelMetrics {

    private static final double ALPHA = 0.2; // EMA smoothing factor

    private final AtomicReference<Double> avgLatencyMs = new AtomicReference<>(0.0);
    private final AtomicLong totalCalls = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);
    private volatile long lastUpdatedEpoch = System.currentTimeMillis();

    public void recordSuccess(long latencyMs) {
        totalCalls.incrementAndGet();
        // Exponential Moving Average: EMA = α × new + (1−α) × old
        avgLatencyMs.updateAndGet(old -> ALPHA * latencyMs + (1 - ALPHA) * old);
        lastUpdatedEpoch = System.currentTimeMillis();
    }

    public void recordError() {
        totalCalls.incrementAndGet();
        errorCount.incrementAndGet();
        // Penalise by boosting apparent latency for EMA
        avgLatencyMs.updateAndGet(old -> ALPHA * (old * 5) + (1 - ALPHA) * old);
        lastUpdatedEpoch = System.currentTimeMillis();
    }

    public double getAvgLatencyMs() {
        return avgLatencyMs.get();
    }

    public double getErrorRate() {
        long calls = totalCalls.get();
        return calls == 0 ? 0.0 : (double) errorCount.get() / calls;
    }

    public long getTotalCalls() {
        return totalCalls.get();
    }

    public long getLastUpdatedEpoch() {
        return lastUpdatedEpoch;
    }

    /**
     * Compute a weighted routing score.
     * Parameters are normalised values in [0,1] passed in by the router after
     * scaling against the current pool of candidates.
     *
     * @param normalisedLatency latency / maxLatencyInPool
     * @param normalisedCost    costPer1kTokens / maxCostInPool
     * @return score — lower is better
     */
    public double computeScore(double normalisedLatency, double normalisedCost) {
        double errorPenalty = getErrorRate() * 2.0; // error rate amplified
        return 0.5 * normalisedLatency + 0.5 * normalisedCost + errorPenalty;
    }

    @Override
    public String toString() {
        return String.format("latency=%.0fms errRate=%.1f%% calls=%d",
                avgLatencyMs.get(), getErrorRate() * 100, totalCalls.get());
    }
}
