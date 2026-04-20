package com.knowledgebot.ai.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * WHY: Session memory resets when the app restarts. ProjectMemoryService
 * persists facts about THE SPECIFIC PROJECT the agent is working on —
 * across restarts, across days — using PgVector for semantic search.
 *
 * WHAT IT STORES (each entry tagged with workspace path):
 *   "This project uses Spring Boot 4.0 with Java 21"
 *   "The main package is com.knowledgebot"
 *   "Database: PostgreSQL via pgvector on port 5433"
 *   "User prefers records over classes for DTOs"
 *   "Completed task: Added UserController on April 18"
 *
 * HOW:
 *   STORE: Text → embedded by Ollama (nomic-embed-text) → saved in PgVector
 *          with metadata { workspace, category, scope, timestamp }.
 *
 *   RECALL: Before every agent task, this service does a semantic cosine
 *           similarity search filtered by workspace path. Top-K results are
 *           formatted and injected into the LLM system prompt.
 *
 * MULTI-WORKSPACE: Each memory is tagged with its workspace path so
 * memories from ProjectA never bleed into ProjectB.
 */
@Service
public class ProjectMemoryService {

    private static final Logger log = LoggerFactory.getLogger(ProjectMemoryService.class);

    private static final double SIMILARITY_THRESHOLD = 0.60;
    private static final int DEFAULT_TOP_K = 5;

    private final VectorStore vectorStore;

    public ProjectMemoryService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    // ─── Store ────────────────────────────────────────────────────────────────

    /**
     * Persist a project-specific fact in the vector store.
     *
     * @param workspacePath Absolute path of the project workspace (partition key)
     * @param fact          Plain-English fact to remember
     * @param category      Tag: "tech-stack", "architecture", "task-history", "preference"
     */
    public void remember(String workspacePath, String fact, String category) {
        Document doc = new Document(fact, Map.of(
            "workspace",  workspacePath,
            "category",   category,
            "scope",      "project",
            "timestamp",  Instant.now().toString()
        ));
        try {
            vectorStore.add(List.of(doc));
            log.info("Remembered [{}]: {}", category, fact.substring(0, Math.min(100, fact.length())));
        } catch (Exception e) {
            // Best-effort: memory failure must not crash the agent task
            log.warn("Failed to store project memory (PgVector may be down): {}", e.getMessage());
        }
    }

    /**
     * Convenience: record what task was completed.
     * Called automatically after every successful {@link com.knowledgebot.ai.agent.AgentLoopService#execute}.
     */
    public void rememberTask(String workspacePath, String taskDescription) {
        remember(workspacePath,
            "Completed task: " + taskDescription.substring(0, Math.min(200, taskDescription.length())),
            "task-history");
    }

    // ─── Recall ───────────────────────────────────────────────────────────────

    /**
     * Recall project memories semantically relevant to the current task.
     *
     * Uses PgVector cosine similarity filtered by workspace path so only
     * memories from THIS project are returned.
     *
     * @param workspacePath The workspace to search in
     * @param context       The current task prompt (used as the search query)
     * @param topK          Maximum number of memories to return
     */
    public List<Document> recall(String workspacePath, String context, int topK) {
        try {
            SearchRequest request = SearchRequest.builder()
                .query(context)
                .topK(topK)
                .similarityThreshold(SIMILARITY_THRESHOLD)
                .filterExpression("workspace == '" + workspacePath.replace("'", "\\'") + "'")
                .build();

            List<Document> results = vectorStore.similaritySearch(request);
            log.debug("Recalled {} project memories for '{}'", results.size(), workspacePath);
            return results;

        } catch (Exception e) {
            // Vector store unavailable: return empty, don't crash the agent
            log.warn("Project memory recall failed (PgVector may be down): {}. Proceeding without context.", e.getMessage());
            return List.of();
        }
    }

    /** Recall with default topK (5). */
    public List<Document> recall(String workspacePath, String context) {
        return recall(workspacePath, context, DEFAULT_TOP_K);
    }

    /**
     * Format recalled memories as a bullet list for LLM system prompt injection.
     *
     * Example output:
     *   - [tech-stack] This project uses Spring Boot 4.0 with Java 21
     *   - [architecture] Main package: com.knowledgebot, multi-module Maven
     *   - [task-history] Completed task: Added UserController on April 18
     */
    public String formatMemoriesForPrompt(List<Document> memories) {
        if (memories.isEmpty()) {
            return "(No previous project context stored yet)";
        }
        return memories.stream()
            .map(doc -> {
                String category = (String) doc.getMetadata().getOrDefault("category", "general");
                return "  - [" + category + "] " + doc.getText();
            })
            .collect(Collectors.joining("\n"));
    }
}
