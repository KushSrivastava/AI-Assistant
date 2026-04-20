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
 * Deletes files from within the workspace.
 *
 * WHY: Sometimes the agent needs to remove a file it created incorrectly,
 * remove a deprecated class, or clean up before regenerating a file.
 *
 * SECURITY:
 *   1. Sandbox boundary check — cannot delete outside workspace
 *   2. ALWAYS requires explicit user permission (no auto-approve possible)
 *   3. Refuses to delete directories (only individual files)
 *      — prevents accidental wipeout of a package
 *
 * The description explicitly tells the LLM to prefer rewriting over deleting.
 */
@Component
public class FileDeleteTool {

    private static final Logger log = LoggerFactory.getLogger(FileDeleteTool.class);

    private final WorkspaceManager workspaceManager;
    private final CommandPermissionService permissionService;

    public FileDeleteTool(WorkspaceManager workspaceManager,
                          CommandPermissionService permissionService) {
        this.workspaceManager = workspaceManager;
        this.permissionService = permissionService;
    }

    @Tool(description = """
        Delete a single file from the workspace. Use with EXTREME CAUTION.
        Prefer rewriting files over deleting them — only delete when truly necessary
        (e.g., removing a deprecated class that has been replaced).
        This tool CANNOT delete directories. Requires user approval.
        """)
    public String deleteFile(
        @ToolParam(description = "Relative path to the file to delete, e.g. 'src/main/java/com/example/OldService.java'")
        String path
    ) {
        try {
            if (!workspaceManager.isWorkspaceAttached()) {
                return "ERROR: No workspace attached.";
            }

            Path fullPath = workspaceManager.resolve(path);
            workspaceManager.validatePathInWorkspace(fullPath);

            if (!Files.exists(fullPath)) {
                return "INFO: File does not exist, nothing to delete: " + path;
            }

            if (Files.isDirectory(fullPath)) {
                return "ERROR: '" + path + "' is a directory. This tool only deletes files. "
                    + "If you need to delete a directory, use runCommand with 'rm -rf dir_name' "
                    + "(subject to user approval).";
            }

            // ALWAYS require explicit permission for deletion — even more critical than writes
            if (!permissionService.requestPermission("⚠️ DELETE FILE (irreversible): " + path)) {
                return "DENIED: User did not approve deletion of '" + path + "'. "
                    + "Consider rewriting the file instead.";
            }

            Files.delete(fullPath);
            log.info("Deleted file: {}", path);
            return "SUCCESS: Deleted '" + path + "'";

        } catch (SecurityException e) {
            return "ERROR: " + e.getMessage();
        } catch (Exception e) {
            log.error("Failed to delete file: {}", path, e);
            return "ERROR deleting '" + path + "': " + e.getMessage();
        }
    }
}
