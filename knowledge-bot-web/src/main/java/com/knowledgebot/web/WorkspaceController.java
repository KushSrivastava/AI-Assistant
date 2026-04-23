package com.knowledgebot.web;

import com.knowledgebot.ai.model.WorkspaceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/workspace")
public class WorkspaceController {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceController.class);

    private final WorkspaceManager workspaceManager;

    public WorkspaceController(WorkspaceManager workspaceManager) {
        this.workspaceManager = workspaceManager;
    }

    @PostMapping("/attach")
    public ResponseEntity<?> attach(@RequestBody Map<String, String> request) {
        String path = request.get("path");
        try {
            workspaceManager.attach(Paths.get(path));
            return ResponseEntity.ok(Map.of("status", "attached", "path", path));
        } catch (IOException e) {
            log.error("Failed to attach workspace: {}", path, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to create or access workspace: " + e.getMessage()));
        }
    }

    @GetMapping("/active")
    public ResponseEntity<?> getActive() {
        if (!workspaceManager.isWorkspaceAttached()) {
            return ResponseEntity.ok(Map.of("attached", false));
        }
        return ResponseEntity.ok(Map.of(
            "attached", true,
            "path", workspaceManager.getActiveWorkspace().toString()
        ));
    }
}
