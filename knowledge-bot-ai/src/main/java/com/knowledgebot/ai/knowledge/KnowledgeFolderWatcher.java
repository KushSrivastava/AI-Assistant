package com.knowledgebot.ai.knowledge;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.*;

/**
 * Watches the knowledge folder for new/modified/deleted files
 * and automatically re-indexes them.
 */
@Service
public class KnowledgeFolderWatcher {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeFolderWatcher.class);
    
    private final KnowledgeIngestionService knowledgeIngestionService;
    private final KnowledgeProperties knowledgeProperties;

    public KnowledgeFolderWatcher(KnowledgeIngestionService knowledgeIngestionService,
                                  KnowledgeProperties knowledgeProperties) {
        this.knowledgeIngestionService = knowledgeIngestionService;
        this.knowledgeProperties = knowledgeProperties;
    }

    @PostConstruct
    public void startWatching() {
        if (!knowledgeProperties.isWatchEnabled()) {
            log.info("Knowledge folder watching is disabled.");
            return;
        }

        Thread.ofVirtual().start(() -> {
            try {
                WatchService watcher = FileSystems.getDefault().newWatchService();
                
                String basePath = knowledgeProperties.getBasePath();
                if (basePath.contains("${user.home}")) {
                    basePath = basePath.replace("${user.home}", System.getProperty("user.home"));
                }
                
                Path knowledgePath = Paths.get(basePath);
                if (!Files.exists(knowledgePath)) {
                    // Will be created by ingestion service
                    return;
                }

                knowledgePath.register(watcher,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY);

                log.info("Started watching knowledge base for changes: {}", knowledgePath);

                while (true) {
                    WatchKey key = watcher.take();
                    for (WatchEvent<?> event : key.pollEvents()) {
                        Path changed = knowledgePath.resolve((Path) event.context());
                        
                        // Simple debounce/filter logic
                        if (Files.isRegularFile(changed)) {
                            log.info("Knowledge file changed/created: {}. Triggering re-index...", changed);
                            knowledgeIngestionService.indexDocument(changed);
                        }
                    }
                    if (!key.reset()) {
                        log.warn("Knowledge watch key no longer valid");
                        break;
                    }
                }
            } catch (Exception e) {
                log.error("Knowledge folder watcher encountered an error", e);
            }
        });
    }
}
