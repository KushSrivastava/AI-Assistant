package com.knowledgebot.core.scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Logic-adapted from OpenClaude: Implements explicit context "mentions" (@filename).
 * Bypasses VectorStore for specific file targets.
 */
@Service
public class MentionParser {
    private static final Logger log = LoggerFactory.getLogger(MentionParser.class);
    private static final Pattern MENTION_PATTERN = Pattern.compile("@([\\\\w.-/]+)");

    /**
     * Parses the query for @mentions and returns the content of the mentioned file if found.
     * @param query The user query (e.g., "Explain @AuthService.java")
     * @param rootDir The workspace root
     * @return Optional containing file content if a mention was found and file exists.
     */
    public Optional<String> parseAndFetchMention(String query, Path rootDir) {
        Matcher matcher = MENTION_PATTERN.matcher(query);
        if (matcher.find()) {
            String fileName = matcher.group(1);
            log.info("Detected @mention for file: {}", fileName);
            
            // Try to find the file in the workspace
            try {
                return Files.walk(rootDir)
                        .filter(p -> p.getFileName().toString().equalsIgnoreCase(fileName) 
                                || p.toString().endsWith(fileName))
                        .filter(Files::isRegularFile)
                        .findFirst()
                        .map(this::readFileContent);
            } catch (IOException e) {
                log.error("Error searching for mentioned file: {}", fileName, e);
            }
        }
        return Optional.empty();
    }

    private String readFileContent(Path path) {
        try {
            String content = Files.readString(path);
            return "\n--- START OF FILE: " + path.getFileName() + " ---\n" +
                   content +
                   "\n--- END OF FILE: " + path.getFileName() + " ---\n";
        } catch (IOException e) {
            log.error("Could not read file: {}", path, e);
            return "[Error: Could not read " + path.getFileName() + "]";
        }
    }
}
