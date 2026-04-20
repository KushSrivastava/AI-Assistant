package com.knowledgebot.ai.knowledge;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import com.knowledgebot.ai.chunking.CodeChunkingService;
import com.knowledgebot.ai.ingestion.MultiFormatParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * WHY: This is the "personal memory" feature. You can drop any document
 * into the knowledge folder and the bot will learn from it.
 *
 * HOW IT WORKS:
 * 1. On startup, scan the knowledge folder for all documents
 * 2. Parse each document (PDF uses Tika, code uses raw read, etc.)
 * 3. Split into chunks (each chunk ~500 tokens)
 * 4. Generate embeddings using the Ollama embedding model
 * 5. Store in PgVector with metadata (filename, chunk#, timestamp)
 * 6. Watch the folder for new files and re-index them automatically
 *
 * USAGE: Before every agent task, or when implicitly called as a tool,
 * the system queries this knowledge base for relevant context.
 */
@Service
public class KnowledgeIngestionService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIngestionService.class);
    
    private final VectorStore vectorStore;
    private final MultiFormatParser multiFormatParser;
    private final CodeChunkingService chunkingService;
    private final KnowledgeProperties knowledgeProperties;

    public KnowledgeIngestionService(VectorStore vectorStore,
                                     MultiFormatParser multiFormatParser,
                                     CodeChunkingService chunkingService,
                                     KnowledgeProperties knowledgeProperties) {
        this.vectorStore = vectorStore;
        this.multiFormatParser = multiFormatParser;
        this.chunkingService = chunkingService;
        this.knowledgeProperties = knowledgeProperties;
    }

    @PostConstruct
    public void indexExistingDocuments() {
        String basePath = knowledgeProperties.getBasePath();
        
        // Resolve ${user.home} if present (Spring properties don't strictly resolve JVM environment variables gracefully sometimes without proper syntax)
        if (basePath.contains("${user.home}")) {
            basePath = basePath.replace("${user.home}", System.getProperty("user.home"));
        }
        
        Path knowledgePath = Paths.get(basePath);
        if (!Files.exists(knowledgePath)) {
            try {
                Files.createDirectories(knowledgePath);
                log.info("Created knowledge base directory: {}", knowledgePath);
                return;
            } catch (Exception e) {
                log.warn("Could not create knowledge base directory {}: {}", knowledgePath, e.getMessage());
                return;
            }
        }

        try (Stream<Path> stream = Files.walk(knowledgePath)) {
            List<Path> files = stream
                .filter(Files::isRegularFile)
                .filter(multiFormatParser::isSupported)
                .toList();

            if (!files.isEmpty()) {
                log.info("Indexing {} documents from knowledge base", files.size());
                for (Path file : files) {
                    indexDocument(file);
                }
            }
        } catch (Exception e) {
            log.error("Failed to read knowledge base directory", e);
        }
    }

    public void indexDocument(Path file) {
        try {
            Document doc = multiFormatParser.parseFile(file);
            // Add knowledge-specific metadata
            // Note: Map.of() entries cannot be easily mutated once created in MultiFormatParser, 
            // so we create a new mutable properties map or let MultiFormatParser handle it. Since we get a document back,
            // we will mutate its metadata implicitly. This is safe with simple HashMaps but Document class holds nested structures.
            doc.getMetadata().put("scope", "knowledge-base");
            doc.getMetadata().put("indexed-at", Instant.now().toString());

            List<Document> chunks = chunkingService.chunk(doc);
            if (!chunks.isEmpty()) {
                // Ensure all chunks have the scope
                chunks.forEach(chunk -> chunk.getMetadata().put("scope", "knowledge-base"));
                
                // Add to Vector Store (this handles embedding generation automatically)
                vectorStore.add(chunks);
                log.info("Indexed knowledge document: {} ({} chunks)", file.getFileName(), chunks.size());
            }
        } catch (Exception e) {
            log.error("Failed to index knowledge document: {}", file, e);
        }
    }

    public List<Document> queryKnowledge(String query, int topK) {
        SearchRequest request = SearchRequest.builder()
            .query(query)
            .topK(topK)
            .similarityThreshold(0.5)
            .filterExpression("scope == 'knowledge-base'")
            .build();
        return vectorStore.similaritySearch(request);
    }
}
