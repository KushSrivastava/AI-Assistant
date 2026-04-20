package com.knowledgebot.ai.tools;

import com.knowledgebot.ai.model.WorkspaceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * WHY: The LLM needs to find where a class, annotation, method, or string is
 * used across the codebase. Without grep-like search it would have to read
 * every file individually — extremely slow and context-wasteful.
 *
 * HOW: Walks all files in the workspace (filtered by extension if provided),
 * searches each for the query string, and returns matching lines with paths.
 * Results are capped at 50 matches to prevent context overflow.
 *
 * USE CASES:
 *  - "Where is @Transactional used?" → searchCodebase("@Transactional", "java")
 *  - "Where is UserService injected?" → searchCodebase("UserService", "java")
 *  - "Find all Spring beans" → searchCodebase("@Service", "java")
 */
@Component
public class SearchCodebaseTool {

    private static final Logger log = LoggerFactory.getLogger(SearchCodebaseTool.class);
    private static final int MAX_RESULTS = 50;
    private static final int MAX_DEPTH = 10;

    private final WorkspaceManager workspaceManager;

    public SearchCodebaseTool(WorkspaceManager workspaceManager) {
        this.workspaceManager = workspaceManager;
    }

    @Tool(description = """
        Search for a text pattern across all files in the workspace — like 'grep'.
        Use this to find where a class, method, annotation, or text string appears.
        Optionally filter by file extension (e.g., 'java', 'xml', 'yml').
        Returns matching file path + line number + matched line content.
        Results are limited to 50 matches.
        """)
    public String searchCodebase(
        @ToolParam(description = "The text to search for, e.g. '@Transactional', 'UserService', 'spring.datasource'")
        String query,
        @ToolParam(description = "Optional file extension filter without dot, e.g. 'java', 'xml', 'yml'. Leave empty to search all files.")
        String fileExtension
    ) {
        try {
            if (!workspaceManager.isWorkspaceAttached()) {
                return "ERROR: No workspace attached.";
            }

            Path workspace = workspaceManager.getActiveWorkspace();
            List<String> results = new ArrayList<>();
            int[] totalMatches = {0};

            try (Stream<Path> walk = Files.walk(workspace, MAX_DEPTH)) {
                walk.filter(Files::isRegularFile)
                    .filter(p -> !isIgnoredPath(p, workspace))
                    .filter(p -> matchesExtension(p, fileExtension))
                    .sorted()
                    .forEach(file -> {
                        if (totalMatches[0] >= MAX_RESULTS) return;

                        try {
                            List<String> lines = Files.readAllLines(file);
                            for (int i = 0; i < lines.size() && totalMatches[0] < MAX_RESULTS; i++) {
                                if (lines.get(i).contains(query)) {
                                    String relativePath = workspace.relativize(file).toString();
                                    results.add(String.format("%s:%d: %s",
                                        relativePath, i + 1, lines.get(i).trim()));
                                    totalMatches[0]++;
                                }
                            }
                        } catch (IOException ignored) {
                            // Skip unreadable files (binary, locked, etc.)
                        }
                    });
            }

            if (results.isEmpty()) {
                return "No results found for '" + query + "'"
                    + (fileExtension != null && !fileExtension.isEmpty() ? " in ." + fileExtension + " files" : "")
                    + " in workspace.";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Found ").append(results.size()).append(" match(es) for '").append(query).append("'");
            if (fileExtension != null && !fileExtension.isEmpty()) {
                sb.append(" [.").append(fileExtension).append(" files only]");
            }
            sb.append(":\n\n");
            results.forEach(r -> sb.append(r).append("\n"));

            if (totalMatches[0] >= MAX_RESULTS) {
                sb.append("\n[Showing first ").append(MAX_RESULTS).append(" matches. Refine your query for more specific results.]");
            }

            log.debug("Search '{}' → {} results", query, results.size());
            return sb.toString();

        } catch (Exception e) {
            log.error("Search failed for query: {}", query, e);
            return "ERROR searching codebase: " + e.getMessage();
        }
    }

    /** Skip build artifacts, IDE files, and node_modules to keep results relevant. */
    private boolean isIgnoredPath(Path file, Path workspace) {
        String relative = workspace.relativize(file).toString().replace('\\', '/');
        return relative.contains("/target/")
            || relative.contains("/node_modules/")
            || relative.contains("/.git/")
            || relative.contains("/.idea/")
            || relative.contains("/.mvn/")
            || relative.contains("/build/")
            || relative.contains("/.gradle/");
    }

    private boolean matchesExtension(Path file, String ext) {
        if (ext == null || ext.isBlank()) return true;
        return file.getFileName().toString().endsWith("." + ext);
    }
}
