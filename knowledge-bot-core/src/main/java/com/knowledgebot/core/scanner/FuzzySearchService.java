package com.knowledgebot.core.scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Logic-adapted from OpenClaude: Implements "Greedy Fallback Search."
 * If vector scores are low, a keyword-based grep search is used across the workspace.
 */
@Service
public class FuzzySearchService {
    private static final Logger log = LoggerFactory.getLogger(FuzzySearchService.class);

    /**
     * Searches for a keyword in all text files in the root directory.
     * @param query The user's query keyword(s)
     * @param rootDir The workspace root
     * @return List of matching line snippets
     */
    public List<String> fallbackGrepSearch(String query, Path rootDir) {
        log.info("Starting Greedy Fallback Search for keyword: {}", query);
        List<String> results = new ArrayList<>();
        
        try {
            List<Path> files = Files.walk(rootDir)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java") || p.toString().endsWith(".py") || p.toString().endsWith(".xml"))
                    .collect(Collectors.toList());

            for (Path file : files) {
                List<String> lines = Files.readAllLines(file);
                for (int i = 0; i < lines.size(); i++) {
                    if (lines.get(i).toLowerCase().contains(query.toLowerCase())) {
                        results.add("\n[MATCH - " + file.getFileName() + ":L" + (i + 1) + "]\n" + lines.get(i));
                    }
                }
                if (results.size() > 50) break; // Limit to 50 results
            }
        } catch (IOException e) {
            log.error("Fuzzy Search failed: {}", e.getMessage());
        }
        return results;
    }
}
