package com.knowledgebot.core.scanner;

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
import java.util.stream.Collectors;

/**
 * Logic-adapted from the user's recommendations: Implements an Indexing Pipeline.
 * Triggers re-indexing when the local environment changes.
 */
@Service
public class IndexingPipeline {
    private static final Logger log = LoggerFactory.getLogger(IndexingPipeline.class);

    private final WorkspaceScannerService scanner;
    private final VectorStore vectorStore;
    private final EnvironmentSensingService environmentSensing;

    public IndexingPipeline(WorkspaceScannerService scanner, VectorStore vectorStore, EnvironmentSensingService environmentSensing) {
        this.scanner = scanner;
        this.vectorStore = vectorStore;
        this.environmentSensing = environmentSensing;
    }

    /**
     * Triggers a full re-index of the workspace.
     * @param rootPath The project root directory
     */
    public void triggerReindex(Path rootPath) {
        log.info("Triggering re-indexing pipeline for: {}", rootPath);
        
        List<Path> files = scanner.scanWorkspace(rootPath);
        log.info("Scanned {} files for indexing.", files.size());

        List<Document> documents = files.stream()
            .map(this::toDocument)
            .filter(java.util.Optional::isPresent)
            .map(java.util.Optional::get)
            .collect(Collectors.toList());

        if (!documents.isEmpty()) {
            vectorStore.add(documents);
            log.info("Successfully indexed {} documents to the vector store.", documents.size());
        }
    }

    private java.util.Optional<Document> toDocument(Path path) {
        try {
            String content = Files.readString(path);
            return java.util.Optional.of(new Document(content, Map.of(
                "source", (Object) path.toString(),
                "filename", path.getFileName().toString()
            )));
        } catch (IOException e) {
            log.error("Failed to read file for indexing: {}", path, e);
            return java.util.Optional.empty();
        }
    }

    public void checkAndReindex(Path rootPath) {
        // Here we could add logic to check Git status or file timestamps
        // For now, we perform a manual trigger if 'git' was recently used or explicitly requested
        boolean gitAvailable = environmentSensing.getSensedTools().containsKey("git");
        if (gitAvailable) {
            log.debug("Git sensed. Re-indexing pipeline can leverage git diff in future version.");
        }
        triggerReindex(rootPath);
    }
}
