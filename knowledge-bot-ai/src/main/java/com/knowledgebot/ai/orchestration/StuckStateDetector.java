package com.knowledgebot.ai.orchestration;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Detects when an agent loop is stuck by maintaining a ring buffer of the last N
 * task results and checking whether they are all identical (or near-identical).
 *
 * Based on the stuck-state detection pattern from OpenManus.
 */
public class StuckStateDetector {

    private static final int DEFAULT_WINDOW = 4;

    /** Minimum Levenshtein-similarity ratio to consider two strings "the same". */
    private static final double SIMILARITY_THRESHOLD = 0.90;

    private final int windowSize;
    private final Deque<String> recentResults;

    public StuckStateDetector() {
        this(DEFAULT_WINDOW);
    }

    public StuckStateDetector(int windowSize) {
        this.windowSize = windowSize;
        this.recentResults = new ArrayDeque<>(windowSize);
    }

    /**
     * Record the latest result and check whether the detector considers the agent stuck.
     *
     * @param result the latest task/step output
     * @return {@code true} if the last {@code windowSize} outputs are all near-identical
     */
    public boolean record(String result) {
        if (recentResults.size() >= windowSize) {
            recentResults.pollFirst();
        }
        recentResults.addLast(normalise(result));
        return isStuck();
    }

    /** Returns {@code true} without recording a new result. */
    public boolean isStuck() {
        if (recentResults.size() < windowSize) {
            return false; // not enough data yet
        }
        String reference = recentResults.peekFirst();
        return recentResults.stream()
                .allMatch(r -> similarity(reference, r) >= SIMILARITY_THRESHOLD);
    }

    public void reset() {
        recentResults.clear();
    }

    // -------------------------------------------------------------------------

    /** Lower-case, strip whitespace runs → canonical form for comparison. */
    private static String normalise(String s) {
        if (s == null) return "";
        return s.toLowerCase().replaceAll("\\s+", " ").trim();
    }

    /**
     * Normalised Levenshtein similarity: 1 - (editDistance / maxLength).
     * Returns 1.0 for identical strings, 0.0 for completely different strings.
     */
    static double similarity(String a, String b) {
        if (a == null || b == null) return 0.0;
        if (a.equals(b)) return 1.0;
        int maxLen = Math.max(a.length(), b.length());
        if (maxLen == 0) return 1.0;
        return 1.0 - (double) editDistance(a, b) / maxLen;
    }

    private static int editDistance(String a, String b) {
        // Use only two rows to keep memory O(min(m,n))
        if (a.length() < b.length()) { String tmp = a; a = b; b = tmp; }
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(prev[j] + 1, curr[j - 1] + 1), prev[j - 1] + cost);
            }
            int[] swap = prev; prev = curr; curr = swap;
        }
        return prev[b.length()];
    }
}
