package com.knowledgebot.ai.tools;

import com.knowledgebot.ai.model.WorkspaceManager;
import com.knowledgebot.core.security.CommandPermissionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Creates directories within the workspace.
 *
 * WHY: Many tasks require creating a new package structure before writing files.
 * e.g., "Create src/main/java/com/example/controller/" before writing a controller.
 * Without this the agent would have to rely on writeFile's parent-dir creation
 * for each file individually — less explicit and harder to reason about.
 *
 * SECURITY: Path is validated to be inside the workspace sandbox.
 */
@Component
public class DirectoryCreateTool {

    private static final Logger log = LoggerFactory.getLogger(DirectoryCreateTool.class);

    private final WorkspaceManager workspaceManager;
    private final CommandPermissionService permissionService;

    public DirectoryCreateTool(WorkspaceManager workspaceManager,
                               CommandPermissionService permissionService) {
        this.workspaceManager = workspaceManager;
        this.permissionService = permissionService;
    }

    @Tool(description = """
        Create a new directory (and all needed parent directories) in the workspace.
        Use this before writing files that need a new package or folder structure.
        Example: 'src/main/java/com/example/service'
        """)
    public String createDirectory(
        @ToolParam(description = "Relative path of the directory to create, e.g. 'src/main/java/com/example/controller'")
        String path
    ) {
        try {
            if (!workspaceManager.isWorkspaceAttached()) {
                return "ERROR: No workspace attached.";
            }

            Path fullPath = workspaceManager.resolve(path);
            workspaceManager.validatePathInWorkspace(fullPath);

            if (Files.exists(fullPath)) {
                return "INFO: Directory already exists: " + path;
            }

            if (!permissionService.requestPermission("Create directory: " + path)) {
                return "DENIED: User did not approve creating directory '" + path + "'.";
            }

            Files.createDirectories(fullPath);
            log.info("Created directory: {}", path);
            return "SUCCESS: Created directory '" + path + "'";

        } catch (SecurityException e) {
            return "ERROR: " + e.getMessage();
        } catch (Exception e) {
            log.error("Failed to create directory: {}", path, e);
            return "ERROR creating directory '" + path + "': " + e.getMessage();
        }
    }
}
