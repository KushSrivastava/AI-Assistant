package com.knowledgebot.ai.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class WorkspaceWatcherService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceWatcherService.class);

    private final VectorStore vectorStore;
    private final MultiFormatParser multiFormatParser;
    private final ConcurrentHashMap<Path, String> fileHashes = new ConcurrentHashMap<>();
    private final ExecutorService watcherExecutor = Executors.newSingleThreadExecutor();
    private volatile boolean watching = false;
    private final AtomicInteger filesIndexed = new AtomicInteger(0);

    public WorkspaceWatcherService(VectorStore vectorStore, MultiFormatParser multiFormatParser) {
        this.vectorStore = vectorStore;
        this.multiFormatParser = multiFormatParser;
    }

    public void startWatching(Path watchDir) {
        if (watching) {
            log.warn("Already watching directory: {}", watchDir);
            return;
        }

        watching = true;
        log.info("Starting workspace watcher on: {}", watchDir);

        watcherExecutor.submit(() -> {
            try {
                WatchService watchService = FileSystems.getDefault().newWatchService();
                watchDir.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE);

                log.info("Watcher started. Monitoring for file changes...");

                while (watching) {
                    WatchKey key = watchService.poll(2, TimeUnit.SECONDS);
                    if (key == null) continue;

                    for (WatchEvent<?> event : key.pollEvents()) {
                        WatchEvent.Kind<?> kind = event.kind();
                        if (kind == StandardWatchEventKinds.OVERFLOW) continue;

                        Path changedFile = watchDir.resolve(((WatchEvent<Path>) event).context());
                        handleFileEvent(changedFile, kind);
                    }

                    boolean valid = key.reset();
                    if (!valid) break;
                }

                watchService.close();
            } catch (Exception e) {
                log.error("Watcher error: {}", e.getMessage(), e);
            }
        });
    }

    public void stopWatching() {
        watching = false;
        watcherExecutor.shutdown();
        log.info("Workspace watcher stopped. Total files indexed: {}", filesIndexed.get());
    }

    public boolean isWatching() {
        return watching;
    }

    public int getFilesIndexed() {
        return filesIndexed.get();
    }

    private void handleFileEvent(Path file, WatchEvent.Kind<?> kind) {
        if (Files.isDirectory(file)) return;
        if (!multiFormatParser.isSupported(file)) return;

        try {
            switch (kind.name()) {
                case "ENTRY_CREATE", "ENTRY_MODIFY" -> handleFileUpdate(file);
                case "ENTRY_DELETE" -> handleFileDelete(file);
            }
        } catch (Exception e) {
            log.error("Error handling file event for {}: {}", file, e.getMessage());
        }
    }

    private void handleFileUpdate(Path file) throws IOException {
        String currentHash = computeHash(file);
        String previousHash = fileHashes.get(file);

        if (currentHash.equals(previousHash)) {
            return;
        }

        log.info("File changed, re-indexing: {}", file.getFileName());

        try {
            Document doc = multiFormatParser.parseFile(file);
            vectorStore.add(List.of(doc));
            fileHashes.put(file, currentHash);
            filesIndexed.incrementAndGet();
            log.info("Indexed: {}", file.getFileName());
        } catch (Exception e) {
            log.error("Failed to index {}: {}", file.getFileName(), e.getMessage());
        }
    }

    private void handleFileDelete(Path file) {
        log.info("File deleted, removing from index: {}", file.getFileName());
        fileHashes.remove(file);
    }

    private String computeHash(Path file) throws IOException {
        byte[] content = Files.readAllBytes(file);
        return Integer.toHexString(java.util.Arrays.hashCode(content));
    }
}
