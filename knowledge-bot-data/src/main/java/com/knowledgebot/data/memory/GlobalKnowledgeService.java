package com.knowledgebot.data.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Logic-adapted from OpenCode: Implements Global Knowledge / "Recallium" Memory.
 * Shares findings and patterns across all local projects.
 */
@Service
public class GlobalKnowledgeService {
    private static final Logger log = LoggerFactory.getLogger(GlobalKnowledgeService.class);
    
    private final VectorStore vectorStore;

    public GlobalKnowledgeService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * Learns a new pattern or fact and stores it in the global persistent memory.
     * @param category The type of knowledge (e.g., "auth", "db-migration", "bug-fix")
     * @param fact The actual description or code pattern
     */
    public void learn(String category, String fact) {
        log.info("Learning new global pattern for category: {}", category);
        Document doc = new Document(fact, Map.of("category", category, "scope", "global"));
        vectorStore.add(List.of(doc));
    }

    /**
     * Recalls global knowledge relevant to a specific query.
     * @param query The context to recall for
     * @return List of relevant global documents
     */
    public List<Document> recall(String query) {
        log.info("Recalling global knowledge for context: {}", query);
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(3)
                .similarityThreshold(0.6)
                .build();
        return vectorStore.similaritySearch(request);
    }
}
