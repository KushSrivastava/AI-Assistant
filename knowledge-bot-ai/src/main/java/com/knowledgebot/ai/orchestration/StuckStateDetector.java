package com.knowledgebot.ai.orchestration;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Detects when an agent loop is stuck by maintaining a ring buffer of the last N
 * task results and checking whether they are all identical (or near-identical).
 *
 * Upgraded for Phase 5: Now maintains a conversational trace buffer to
 * export "Correction Triumphs" for Supervised Fine-Tuning.
 */
public class StuckStateDetector {

    private static final int DEFAULT_WINDOW = 4;
    private static final double SIMILARITY_THRESHOLD = 0.90;

    private final int windowSize;
    private final Deque<String> recentResults;

    // --- Phase 5: SFT Dataset Buffer ---
    private final List<Map<String, String>> conversationHistory = new ArrayList<>();

    public StuckStateDetector() {
        this(DEFAULT_WINDOW);
    }

    public StuckStateDetector(int windowSize) {
        this.windowSize = windowSize;
        this.recentResults = new ArrayDeque<>(windowSize);

        // Phase 5: Initialize the fine-tuning dataset with the System Prompt
        Map<String, String> sysMsg = new HashMap<>();
        sysMsg.put("role", "system");
        sysMsg.put("content", "You are an autonomous AI executing a specific software task. Follow instructions strictly and correct yourself if you fail.");
        conversationHistory.add(sysMsg);
    }

    // ========================================================================
    // NEW PHASE 5 METHODS (Data Loop & Experience Tracking)
    // ========================================================================

    /**
     * Records the execution attempt for the SFT dataset, then uses the existing
     * fuzzy-matching logic to determine if the agent is stuck.
     */
    public boolean recordAttempt(String prompt, String result) {
        // Track the user prompt
        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", prompt);
        conversationHistory.add(userMsg);

        // Track the LLM response
        Map<String, String> assistantMsg = new HashMap<>();
        assistantMsg.put("role", "assistant");
        assistantMsg.put("content", result);
        conversationHistory.add(assistantMsg);

        // Delegate to original Levenshtein logic to determine stuck state
        return record(result);
    }

    /**
     * A "Correction Triumph" occurs when the agent fails on Attempt 1,
     * gets corrected, and succeeds on Attempt 2 or 3.
     */
    public boolean isCorrectionTriumph() {
        // If history > 3 items (Sys + User + Asst), multiple attempts occurred!
        return conversationHistory.size() > 3;
    }

    public List<Map<String, String>> getDatasetMessages() {
        return conversationHistory;
    }

    // ========================================================================
    // ORIGINAL METHODS PRESERVED BELOW (Fuzzy Matching & Ring Buffer)
    // ========================================================================

    public boolean record(String result) {
        if (recentResults.size() >= windowSize) {
            recentResults.pollFirst();
        }
        recentResults.addLast(normalise(result));
        return isStuck();
    }

    public boolean isStuck() {
        if (recentResults.size() < windowSize) {
            return false;
        }
        String reference = recentResults.peekFirst();
        return recentResults.stream()
                .allMatch(r -> similarity(reference, r) >= SIMILARITY_THRESHOLD);
    }

    public void reset() {
        recentResults.clear();
    }

    private static String normalise(String s) {
        if (s == null) return "";
        return s.toLowerCase().replaceAll("\\s+", " ").trim();
    }

    static double similarity(String a, String b) {
        if (a == null || b == null) return 0.0;
        if (a.equals(b)) return 1.0;
        int maxLen = Math.max(a.length(), b.length());
        if (maxLen == 0) return 1.0;
        return 1.0 - (double) editDistance(a, b) / maxLen;
    }

    private static int editDistance(String a, String b) {
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