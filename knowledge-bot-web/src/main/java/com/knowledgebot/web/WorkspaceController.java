package com.knowledgebot.web;

import com.knowledgebot.ai.model.WorkspaceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * REST endpoints for workspace management.
 *
 * WHY: The agent can't do anything without knowing WHERE to work.
 * These endpoints let the UI or CLI attach/detach the active workspace
 * and check its status.
 *
 * Endpoints:
 *   POST /api/v1/workspace/attach  — Set the active workspace directory
 *   GET  /api/v1/workspace/status  — Check if workspace is set and its path
 *   POST /api/v1/workspace/detach  — Clear the active workspace
 *
 * These paths are always EXEMPT from the WorkspaceRequiredFilter so the
 * user can attach a workspace even when none is set.
 */
@RestController
@RequestMapping("/api/v1/workspace")
@CrossOrigin
public class WorkspaceController {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceController.class);

    private final WorkspaceManager workspaceManager;

    public WorkspaceController(WorkspaceManager workspaceManager) {
        this.workspaceManager = workspaceManager;
    }

    /**
     * Attach a workspace directory.
     *
     * Request:  { "path": "D:/my-project" }
     * Response: { "status": "attached", "path": "D:/my-project", "exists": true }
     */
    @PostMapping("/attach")
    public ResponseEntity<Map<String, Object>> attach(@RequestBody Map<String, String> request) {
        String pathStr = request.get("path");

        if (pathStr == null || pathStr.isBlank()) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Missing 'path' in request body"));
        }

        Path path = Paths.get(pathStr);
        boolean existed = Files.exists(path);

        try {
            workspaceManager.attach(path);
            log.info("Workspace attached via API: {}", path);

            return ResponseEntity.ok(Map.of(
                "status",  "attached",
                "path",    workspaceManager.getActiveWorkspace().toString(),
                "existed", existed,
                "message", existed
                    ? "Workspace attached successfully."
                    : "Workspace directory created and attached."
            ));
        } catch (IOException e) {
            log.error("Failed to attach workspace: {}", pathStr, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "error",   "Failed to attach workspace",
                    "details", e.getMessage()
                ));
        }
    }

    /**
     * Get the current workspace status.
     *
     * Response: { "attached": true, "path": "D:/my-project" }
     *        or { "attached": false, "path": "" }
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        boolean attached = workspaceManager.isWorkspaceAttached();
        return ResponseEntity.ok(Map.of(
            "attached", attached,
            "path",     attached ? workspaceManager.getActiveWorkspace().toString() : ""
        ));
    }

    /**
     * Detach the current workspace. Session memory is also cleared since
     * all previous context is now irrelevant.
     *
     * Response: { "status": "detached" }
     */
    @PostMapping("/detach")
    public ResponseEntity<Map<String, String>> detach() {
        workspaceManager.detach();
        log.info("Workspace detached via API");
        return ResponseEntity.ok(Map.of(
            "status",  "detached",
            "message", "Workspace detached. Session memory preserved (use /agent/clear to reset)."
        ));
    }
}
