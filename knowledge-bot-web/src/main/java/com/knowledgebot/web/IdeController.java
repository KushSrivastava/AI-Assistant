package com.knowledgebot.web;

import com.knowledgebot.ai.model.WorkspaceManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ide")
public class IdeController {

    private final WorkspaceManager workspaceManager;

    public IdeController(WorkspaceManager workspaceManager) {
        this.workspaceManager = workspaceManager;
    }

    @PostMapping("/open")
    public ResponseEntity<String> openInIde(@RequestBody Map<String, String> request) {
        String filePath = request.get("path");
        String editor = request.getOrDefault("editor", "vscode");

        Path fullPath = workspaceManager.resolve(filePath);

        try {
            ProcessBuilder pb;
            if ("intellij".equals(editor)) {
                // IntelliJ IDEA command line
                // On Windows/Linux `idea` via PATH or absolute paths are needed usually 
                pb = new ProcessBuilder("idea", fullPath.toString());
            } else {
                // VS Code command line
                // The executable is `code` or `code.cmd` on Windows
                String codeExecutable = System.getProperty("os.name").toLowerCase().contains("win") ? "code.cmd" : "code";
                pb = new ProcessBuilder(codeExecutable, fullPath.toString());
            }
            // inherit IO to see errors loosely or ensure process doesn't hang
            pb.inheritIO();
            pb.start();
            return ResponseEntity.ok("Opened in " + editor);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Failed to open editor: " + e.getMessage());
        }
    }
}
