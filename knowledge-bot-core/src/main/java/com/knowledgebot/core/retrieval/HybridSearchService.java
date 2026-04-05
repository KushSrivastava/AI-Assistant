package com.knowledgebot.core.retrieval;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Logic-adapted from the user's recommendations: Implements Hybrid Search.
 * Combines Semantic Search (PGVector) with Keyword Search (BM25/Full-text).
 */
@Service
public class HybridSearchService {
    private static final Logger log = LoggerFactory.getLogger(HybridSearchService.class);

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;

    public HybridSearchService(VectorStore vectorStore, JdbcTemplate jdbcTemplate) {
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Performs a hybrid search by merging vector and keyword results.
     * @param query The user's query
     * @param topK Number of results to return
     * @return Merged list of unique documents
     */
    public List<Document> hybridSearch(String query, int topK) {
        log.info("Performing Hybrid Search for: {}", query);

        // 1. Semantic Search
        List<Document> vectorDocs = vectorStore.similaritySearch(
                SearchRequest.builder().query(query).topK(topK).build());

        // 2. Keyword Search (Simulated BM25 using Postgres Full-Text Search)
        List<Document> keywordDocs = performKeywordSearch(query, topK);

        // 3. Merging (Reciprocal Rank Fusion or simple union)
        Map<String, Document> merged = vectorDocs.stream()
                .collect(Collectors.toMap(Document::getId, d -> d, (existing, replacement) -> existing));
        
        for (Document doc : keywordDocs) {
            merged.putIfAbsent(doc.getId(), doc);
        }

        return merged.values().stream().limit(topK).collect(Collectors.toList());
    }

    private List<Document> performKeywordSearch(String query, int topK) {
        // This assumes a 'global_knowledge_vectors' table with 'content' and 'metadata' columns
        String sql = "SELECT id, content, metadata FROM global_knowledge_vectors " +
                     "WHERE to_tsvector('english', content) @@ plainto_tsquery('english', ?) " +
                     "LIMIT ?";
        
        try {
            return jdbcTemplate.query(sql, (rs, rowNum) -> {
                String id = rs.getString("id");
                String content = rs.getString("content");
                // Simplified metadata parsing
                return new Document(content, Map.of("id", (Object) id, "source", "keyword-tsquery"));
            }, query, topK);
        } catch (Exception e) {
            log.warn("Keyword search failed, falling back to empty list: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
}
