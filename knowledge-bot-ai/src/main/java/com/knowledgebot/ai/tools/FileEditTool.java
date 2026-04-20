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
 * WHY: Writing an entire file just to change 3 lines is wasteful and risky.
 * It overwrites the whole file, creating a chance of accidentally removing
 * unrelated code. FileEditTool is a surgical find-and-replace within a file.
 *
 * WHEN TO USE:
 *   - Fixing a bug in a specific method
 *   - Adding an import statement
 *   - Changing a configuration value
 *   - Updating a method signature
 *
 * WHEN NOT TO USE:
 *   - When you need to restructure the whole file → use writeFile
 *   - When the search text appears multiple times (ambiguous replacement)
 *
 * HOW IT WORKS:
 *   1. Read the file
 *   2. Find exactly one occurrence of searchText
 *   3. Replace with replaceText
 *   4. Write back
 *   If searchText is not found: tell the agent to re-read the file first.
 */
@Component
public class FileEditTool {

    private static final Logger log = LoggerFactory.getLogger(FileEditTool.class);

    private final WorkspaceManager workspaceManager;
    private final CommandPermissionService permissionService;

    public FileEditTool(WorkspaceManager workspaceManager,
                        CommandPermissionService permissionService) {
        this.workspaceManager = workspaceManager;
        this.permissionService = permissionService;
    }

    @Tool(description = """
        Edit a specific section of a file by finding and replacing text.
        Preferred over writeFile when you only need to change a small part of a file.
        The searchText must appear EXACTLY ONCE in the file — if it appears multiple
        times or not at all, the tool returns an error and you should use readFile first
        to see the current content, then provide a more unique searchText.
        Requires user approval in safe mode.
        """)
    public String editFile(
        @ToolParam(description = "Relative path of the file to edit, e.g. 'src/main/resources/application.yml'")
        String path,
        @ToolParam(description = "The exact text to find in the file (must be unique within the file, include surrounding lines for uniqueness)")
        String searchText,
        @ToolParam(description = "The replacement text that will replace the searchText")
        String replaceText
    ) {
        try {
            if (!workspaceManager.isWorkspaceAttached()) {
                return "ERROR: No workspace attached.";
            }

            Path fullPath = workspaceManager.resolve(path);
            workspaceManager.validatePathInWorkspace(fullPath);

            if (!Files.exists(fullPath)) {
                return "ERROR: File not found: " + path + ". Use writeFile to create it.";
            }
            if (Files.isDirectory(fullPath)) {
                return "ERROR: '" + path + "' is a directory, not a file.";
            }

            String content = Files.readString(fullPath);

            // Count occurrences — must be exactly 1 for unambiguous replacement
            int firstIdx  = content.indexOf(searchText);
            if (firstIdx == -1) {
                return "ERROR: searchText not found in '" + path + "'.\n"
                    + "Use readFile to see the current content and pick a unique searchText.\n"
                    + "Searched for: " + searchText.substring(0, Math.min(200, searchText.length()));
            }

            int secondIdx = content.indexOf(searchText, firstIdx + 1);
            if (secondIdx != -1) {
                return "ERROR: searchText appears more than once in '" + path + "'.\n"
                    + "Provide a longer, more unique searchText snippet (include surrounding lines).\n"
                    + "First occurrence at char " + firstIdx + ", second at char " + secondIdx + ".";
            }

            if (!permissionService.requestPermission("Edit file: " + path)) {
                return "DENIED: User did not approve editing '" + path + "'.";
            }

            String updated = content.replace(searchText, replaceText);
            Files.writeString(fullPath, updated,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);

            int lineNumber = content.substring(0, firstIdx).split("\n", -1).length;
            log.info("Edited file: {} at line ~{}", path, lineNumber);
            return "SUCCESS: Replaced text in '" + path + "' at approximately line " + lineNumber;

        } catch (SecurityException e) {
            return "ERROR: " + e.getMessage();
        } catch (Exception e) {
            log.error("Failed to edit file: {}", path, e);
            return "ERROR editing '" + path + "': " + e.getMessage();
        }
    }
}
