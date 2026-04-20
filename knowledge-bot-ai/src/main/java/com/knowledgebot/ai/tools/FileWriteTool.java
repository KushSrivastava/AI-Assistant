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
import java.nio.file.StandardOpenOption;

/**
 * WHY: The LLM needs to write, create, and overwrite files to complete coding tasks.
 *
 * HOW: The LLM provides a relative path and the full file content. This tool:
 *   1. Validates the path is inside the workspace (sandbox security)
 *   2. Requests user permission if Safe Mode is enabled
 *   3. Creates parent directories automatically
 *   4. Writes the content to disk
 *
 * SECURITY: Path traversal attacks (e.g., "../../etc/hosts") are blocked by
 * the workspace boundary check.
 */
@Component
public class FileWriteTool {

    private static final Logger log = LoggerFactory.getLogger(FileWriteTool.class);

    private final WorkspaceManager workspaceManager;
    private final CommandPermissionService permissionService;

    public FileWriteTool(WorkspaceManager workspaceManager,
                         CommandPermissionService permissionService) {
        this.workspaceManager = workspaceManager;
        this.permissionService = permissionService;
    }

    @Tool(description = """
        Write content to a file in the workspace. Creates parent directories if needed.
        Use this to create new files or completely overwrite existing ones.
        Provide the COMPLETE file content — this is not an append operation.
        Always run the build after writing multiple files to catch compilation errors early.
        """)
    public String writeFile(
        @ToolParam(description = "Relative path for the file, e.g. 'src/main/java/com/example/UserController.java'")
        String path,
        @ToolParam(description = "The COMPLETE file content to write. Include all imports, class declaration, and methods.")
        String content
    ) {
        try {
            if (!workspaceManager.isWorkspaceAttached()) {
                return "ERROR: No workspace attached. Cannot write files without a workspace.";
            }

            Path fullPath = workspaceManager.resolve(path);

            // SECURITY: Sandbox boundary check
            if (!fullPath.startsWith(workspaceManager.getActiveWorkspace())) {
                return "ERROR: Path '" + path + "' is outside workspace boundary. Access denied.";
            }

            // PERMISSION: Respect Safe Mode
            if (!permissionService.requestPermission("Write file: " + path)) {
                return "DENIED: User did not approve writing to '" + path + "'. Try a different approach or ask for clarification.";
            }

            // Create parent directories if they don't exist
            Path parent = fullPath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
                log.info("Created directory: {}", parent);
            }

            Files.writeString(fullPath, content,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);

            log.info("Wrote file: {} ({} chars)", path, content.length());
            return "SUCCESS: Written to '" + path + "' (" + content.length() + " chars, "
                + content.lines().count() + " lines)";

        } catch (Exception e) {
            log.error("Failed to write file: {}", path, e);
            return "ERROR writing '" + path + "': " + e.getMessage();
        }
    }
}
