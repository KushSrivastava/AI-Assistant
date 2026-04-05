package com.knowledgebot.ai.pipeline;

import com.knowledgebot.ai.chunking.CodeChunkingService;
import com.knowledgebot.ai.ingestion.MultiFormatParser;
import com.knowledgebot.core.scanner.SemanticIndexingService;
import org.apache.tika.exception.TikaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Indexes workspace files into the vector store.
 *
 * For Java files, uses {@link SemanticIndexingService} (JavaParser-based) to produce
 * a dense semantic summary (class/method signatures, import graph, call graph) instead
 * of raw text.  This dramatically improves embedding quality for code Q&A.
 *
 * For all other file types, falls back to MultiFormatParser (Apache Tika) or raw text.
 */
@Service
public class EmbeddingPipeline {
    private static final Logger log = LoggerFactory.getLogger(EmbeddingPipeline.class);

    private final CodeChunkingService chunkingService;
    private final VectorStore vectorStore;
    private final MultiFormatParser multiFormatParser;
    private final SemanticIndexingService semanticIndexingService;

    public EmbeddingPipeline(CodeChunkingService chunkingService,
                             VectorStore vectorStore,
                             MultiFormatParser multiFormatParser,
                             SemanticIndexingService semanticIndexingService) {
        this.chunkingService = chunkingService;
        this.vectorStore = vectorStore;
        this.multiFormatParser = multiFormatParser;
        this.semanticIndexingService = semanticIndexingService;
    }

    public void processAndEmbed(List<Path> files) {
        for (Path file : files) {
            try {
                Document doc = buildDocument(file);
                List<Document> chunks = chunkingService.chunk(doc);
                vectorStore.add(chunks);
                log.info("Indexed: {} ({} chunks, semantic={})",
                        file.getFileName(), chunks.size(),
                        semanticIndexingService.supports(file));
            } catch (IOException | TikaException e) {
                log.error("Failed reading file {}", file, e);
            }
        }
    }

    private Document buildDocument(Path file) throws IOException, TikaException {
        String fileName = file.getFileName().toString();
        String ext = getExtension(fileName);

        // Java files: use SemanticIndexingService for dense LSP-style summaries
        if (semanticIndexingService.supports(file)) {
            String semanticContent = semanticIndexingService.buildSemanticSummary(file);
            List<String> callGraph = semanticIndexingService.extractCallGraph(file);
            List<String> importGraph = semanticIndexingService.extractImportGraph(file);

            String enriched = semanticContent
                    + (callGraph.isEmpty() ? "" : "\n// Call graph:\n// " + String.join("\n// ", callGraph))
                    + (importGraph.isEmpty() ? "" : "\n// Imports: " + String.join(", ", importGraph));

            return new Document(enriched, Map.of(
                    "fileName", fileName,
                    "absolutePath", file.toAbsolutePath().toString(),
                    "fileExtension", ext,
                    "indexType", (Object) "semantic"
            ));
        }

        // Other formats: use MultiFormatParser (Tika) or raw text fallback
        if (multiFormatParser.isSupported(file)) {
            return multiFormatParser.parseFile(file);
        }

        String content = Files.readString(file);
        return new Document(content, Map.of(
                "fileName", fileName,
                "absolutePath", file.toAbsolutePath().toString(),
                "fileExtension", ext,
                "indexType", (Object) "raw"
        ));
    }

    private String getExtension(String fileName) {
        int idx = fileName.lastIndexOf('.');
        return idx > 0 ? fileName.substring(idx + 1) : "";
    }
}
