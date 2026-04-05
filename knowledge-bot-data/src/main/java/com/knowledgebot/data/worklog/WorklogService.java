package com.knowledgebot.data.worklog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Logic-adapted from OpenCode: Implements session persistence via Worklogs.
 * Writes summaries to a local .kb_memory directory.
 */
@Service
public class WorklogService {
    private static final Logger log = LoggerFactory.getLogger(WorklogService.class);
    private static final String MEMORY_DIR = ".kb_memory";

    /**
     * Appends a summary of work to the local project's worklog.
     * @param rootDir The project root
     * @param summary The work summary to persist
     */
    public void persistWork(Path rootDir, String summary) {
        Path memoryPath = rootDir.resolve(MEMORY_DIR);
        try {
            if (!Files.exists(memoryPath)) {
                Files.createDirectories(memoryPath);
                log.info("Created .kb_memory directory at {}", memoryPath);
            }

            Path worklogFile = memoryPath.resolve("WORKLOG.md");
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String entry = String.format("\n### [%s] Work Summary\n%s\n---\n", timestamp, summary);
            
            Files.writeString(worklogFile, entry, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            log.info("Persisted worklog entry to {}", worklogFile);
            
        } catch (IOException e) {
            log.error("Failed to persist worklog: {}", e.getMessage());
        }
    }

    /**
     * Reads the entire worklog for context.
     * @param rootDir The project root
     * @return The combined worklog content
     */
    public String getRecentContext(Path rootDir) {
        Path worklogFile = rootDir.resolve(MEMORY_DIR).resolve("WORKLOG.md");
        if (Files.exists(worklogFile)) {
            try {
                return Files.readString(worklogFile);
            } catch (IOException e) {
                log.error("Failed to read worklog: {}", e.getMessage());
            }
        }
        return "";
    }
}
