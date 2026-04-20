package com.knowledgebot.web;

import com.knowledgebot.ai.architect.ArchitectService;
import com.knowledgebot.ai.architect.ArchitectService.ArchitectResult;
import com.knowledgebot.ai.model.WorkspaceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * REST endpoints for Architect Mode (Phase 6).
 *
 * Endpoints:
 *   POST /api/v1/architect        — Full HLD→LLD→Code pipeline (may take 5-15 min)
 *   POST /api/v1/architect/hld    — Generate HLD only (design review mode)
 *   POST /api/v1/architect/lld    — Generate LLD from provided HLD
 */
@RestController
@RequestMapping("/api/v1/architect")
@CrossOrigin
public class ArchitectController {

    private static final Logger log = LoggerFactory.getLogger(ArchitectController.class);

    private final ArchitectService architectService;
    private final WorkspaceManager workspaceManager;

    public ArchitectController(ArchitectService architectService,
                               WorkspaceManager workspaceManager) {
        this.architectService = architectService;
        this.workspaceManager = workspaceManager;
    }

    /**
     * Full architect pipeline: HLD → LLD → implement everything.
     *
     * ⚠️  WARNING: This is a long-running operation. May take 5-20 minutes
     * for complex goals because the agent runs the build loop after each file.
     * Consider using /architect/stream (Phase 9 UI will implement this)
     * for real-time progress via SSE.
     *
     * Request:  { "goal": "Build a Spring Boot REST API for a library system" }
     * Response: { "hld": "...", "lld": "...", "implementation": "..." }
     */
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> architect(@RequestBody Map<String, String> request) {
        String goal = request.get("goal");

        if (goal == null || goal.isBlank()) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Missing 'goal' in request body"));
        }

        if (!workspaceManager.isWorkspaceAttached()) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED)
                .body(Map.of(
                    "error",   "WORKSPACE_REQUIRED",
                    "message", "Attach a workspace before running Architect Mode."
                ));
        }

        log.info("Architect mode requested for goal: {}", goal);

        try {
            ArchitectResult result = architectService.architect(goal);
            return ResponseEntity.ok(Map.of(
                "goal",           result.goal(),
                "hld",            result.hld(),
                "lld",            result.lld(),
                "implementation", result.implementation(),
                "docsPath",       "docs/HLD.md and docs/LLD.md saved to workspace"
            ));
        } catch (Exception e) {
            log.error("Architect mode failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "error",   "Architect mode failed",
                    "details", e.getMessage()
                ));
        }
    }

    /**
     * Generate HLD only — useful for design review before committing to full implementation.
     *
     * Request:  { "goal": "..." }
     * Response: { "hld": "...", "savedTo": "docs/HLD.md" }
     */
    @PostMapping(value = "/hld", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> generateHld(@RequestBody Map<String, String> request) {
        String goal = request.get("goal");
        if (goal == null || goal.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing 'goal'"));
        }

        try {
            String hld = architectService.generateHld(goal);
            return ResponseEntity.ok(Map.of(
                "hld",      hld,
                "savedTo",  workspaceManager.isWorkspaceAttached() ? "docs/HLD.md" : "(no workspace)"
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Generate LLD from a provided HLD string.
     *
     * Request:  { "hld": "## 1. System Overview\n..." }
     * Response: { "lld": "...", "savedTo": "docs/LLD.md" }
     */
    @PostMapping(value = "/lld", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> generateLld(@RequestBody Map<String, String> request) {
        String hld = request.get("hld");
        if (hld == null || hld.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing 'hld'"));
        }

        try {
            String lld = architectService.generateLld(hld);
            return ResponseEntity.ok(Map.of(
                "lld",     lld,
                "savedTo", workspaceManager.isWorkspaceAttached() ? "docs/LLD.md" : "(no workspace)"
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", e.getMessage()));
        }
    }
}
