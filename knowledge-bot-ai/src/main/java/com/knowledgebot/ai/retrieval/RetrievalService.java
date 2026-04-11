package com.knowledgebot.ai.retrieval;

import com.knowledgebot.core.performance.HardwareMetricsService;
import com.knowledgebot.core.performance.TokenBudgetService;
import com.knowledgebot.core.scanner.FuzzySearchService;
import com.knowledgebot.core.scanner.MentionParser;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class RetrievalService {

    private static final long SIGNATURE_PRUNE_THRESHOLD = 2000;

    private final VectorStore vectorStore;
    private final MentionParser mentionParser;
    private final FuzzySearchService fuzzySearchService;
    private final TokenBudgetService tokenBudgetService;
    private final HardwareMetricsService hardwareMetrics;
    private static final int DEFAULT_MAX_TOKENS = 32000;

    public RetrievalService(VectorStore vectorStore, MentionParser mentionParser, FuzzySearchService fuzzySearchService, TokenBudgetService tokenBudgetService, HardwareMetricsService hardwareMetrics) {
        this.vectorStore = vectorStore;
        this.mentionParser = mentionParser;
        this.fuzzySearchService = fuzzySearchService;
        this.tokenBudgetService = tokenBudgetService;
        this.hardwareMetrics = hardwareMetrics;
    }

    public List<Document> search(String query, int topK) {
        return search(query, topK, true);
    }

    public List<Document> search(String query, int topK, boolean autoPrune) {
        // 1. Explicit Mention Check (@filename)
        Optional<String> mentionContent = mentionParser.parseAndFetchMention(query, Paths.get("."));
        if (mentionContent.isPresent()) {
            String content = mentionContent.get();
            if (autoPrune && tokenBudgetService.estimateTokens(content) > SIGNATURE_PRUNE_THRESHOLD) {
                String pruned = tokenBudgetService.extractMethodSignatures(content);
                return List.of(new Document(pruned, java.util.Map.of("pruned", (Object) "true")));
            }
            return List.of(new Document(content));
        }

        // 2. Standard Vector Search
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(0.7)
                .build();
        
        List<Document> results = vectorStore.similaritySearch(request);
        
        // 3. Auto-prune large documents to prevent "Lost in the Middle" degradation
        if (autoPrune) {
            results = results.stream()
                    .map(this::pruneIfLarge)
                    .collect(Collectors.toList());
        }
        
        // 4. Greedy Fallback (if no high-confidence results)
        if (results.isEmpty()) {
            List<String> grepMatches = fuzzySearchService.fallbackGrepSearch(query, Paths.get("."));
            if (!grepMatches.isEmpty()) {
                String consolidated = "### Greedy Fallback Results (Keyword Search)\n" + String.join("\n", grepMatches);
                return List.of(new Document(consolidated));
            }
        }
        
        return results;
    }

    private Document pruneIfLarge(Document doc) {
        long tokens = tokenBudgetService.estimateTokens(doc.getText());
        if (tokens > SIGNATURE_PRUNE_THRESHOLD) {
            String pruned = tokenBudgetService.extractMethodSignatures(doc.getText());
            java.util.Map<String, Object> meta = new java.util.HashMap<>(doc.getMetadata());
            meta.put("originalTokens", tokens);
            meta.put("pruned", "true");
            meta.put("prunedTokens", tokenBudgetService.estimateTokens(pruned));
            return new Document(pruned, meta);
        }
        return doc;
    }
}
