package com.knowledgebot.ai.tools;

import com.knowledgebot.ai.model.WorkspaceManager;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Path;

@Component
public class DirectoryListTool {

    private final WorkspaceManager workspaceManager;

    public DirectoryListTool(WorkspaceManager workspaceManager) {
        this.workspaceManager = workspaceManager;
    }

    @Tool(description = "List all files and subdirectories at a given path in the workspace. Use '.' for the workspace root.")
    public String listDirectory(
        @ToolParam(description = "Relative directory path, e.g. 'src/main/java' or '.'") String path
    ) {
        if (!workspaceManager.isWorkspaceAttached()) {
            return "ERROR: No workspace attached.";
        }

        Path fullPath = workspaceManager.resolve(path);
        File dir = fullPath.toFile();

        if (!dir.exists() || !dir.isDirectory()) {
            return "ERROR: Path not found or is not a directory: " + path;
        }

        File[] files = dir.listFiles();
        if (files == null) return "Directory is empty.";

        StringBuilder sb = new StringBuilder();
        sb.append("Contents of ").append(path).append(":\n");
        for (File f : files) {
            sb.append(f.isDirectory() ? "[DIR] " : "[FILE] ").append(f.getName()).append("\n");
        }

        return sb.toString();
    }
}
