package com.knowledgebot.ai.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Service
public class KnowledgeIngestionService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIngestionService.class);
    private final VectorStore vectorStore;

    public KnowledgeIngestionService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public void indexDocument(Path filePath) {
        try {
            log.info("Ingesting document: {}", filePath);
            TikaDocumentReader reader = new TikaDocumentReader(filePath.toUri().toString());
            List<Document> documents = reader.get();

            TokenTextSplitter splitter = new TokenTextSplitter();
            List<Document> chunks = splitter.apply(documents);

            // Tag documents with metadata for filtering
            chunks.forEach(doc -> doc.getMetadata().put("scope", "knowledge-base"));

            vectorStore.add(chunks);
            log.info("Successfully indexed {} chunks from {}", chunks.size(), filePath.getFileName());
        } catch (Exception e) {
            log.error("Failed to index document {}: {}", filePath, e.getMessage());
        }
    }
    
    public List<Document> queryKnowledge(String query, int topK) {
        SearchRequest request = SearchRequest.builder()
            .query(query)
            .topK(topK)
            .similarityThreshold(0.7)
            .filterExpression("scope == 'knowledge-base'")
            .build();
            
        return vectorStore.similaritySearch(request);
    }
}
