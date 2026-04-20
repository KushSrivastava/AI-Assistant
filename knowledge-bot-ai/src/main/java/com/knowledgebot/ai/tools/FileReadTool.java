package com.knowledgebot.ai.tools;

import com.knowledgebot.ai.model.WorkspaceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * WHY: The LLM needs to read files in the workspace to understand a project
 * before making changes. Without this the agent is "coding blind".
 *
 * HOW: When the LLM decides it needs to read a file, Spring AI automatically
 * calls this method, passes the arguments the LLM chose, and returns the
 * result back to the LLM as a tool response message.
 *
 * SECURITY: All paths are validated to be inside the workspace sandbox.
 * Files larger than 50,000 chars are truncated to prevent context overflow.
 */
@Component
public class FileReadTool {

    private static final Logger log = LoggerFactory.getLogger(FileReadTool.class);
    private static final int MAX_CONTENT_LENGTH = 50_000;

    private final WorkspaceManager workspaceManager;

    public FileReadTool(WorkspaceManager workspaceManager) {
        this.workspaceManager = workspaceManager;
    }

    @Tool(description = """
        Read the contents of a file in the workspace. Use this to understand existing code,
        configurations, or any file before making changes. Always read before you write!
        Returns the full file content, or an error if the file doesn't exist.
        """)
    public String readFile(
        @ToolParam(description = "Relative path to the file within the workspace, e.g. 'src/main/java/App.java' or 'pom.xml'")
        String path
    ) {
        try {
            if (!workspaceManager.isWorkspaceAttached()) {
                return "ERROR: No workspace attached. Ask the user to attach a workspace first.";
            }

            Path fullPath = workspaceManager.resolve(path);

            // SECURITY: Sandbox boundary check
            if (!fullPath.startsWith(workspaceManager.getActiveWorkspace())) {
                return "ERROR: Path '" + path + "' is outside the workspace boundary. Access denied.";
            }

            if (!Files.exists(fullPath)) {
                return "ERROR: File not found: " + path + ". Use listDirectory to explore the project structure.";
            }

            if (Files.isDirectory(fullPath)) {
                return "ERROR: '" + path + "' is a directory, not a file. Use listDirectory to list its contents.";
            }

            String content = Files.readString(fullPath);
            log.debug("Read file: {} ({} chars)", path, content.length());

            if (content.length() > MAX_CONTENT_LENGTH) {
                return content.substring(0, MAX_CONTENT_LENGTH)
                    + "\n\n... [TRUNCATED — file is " + content.length()
                    + " chars, showing first " + MAX_CONTENT_LENGTH + "]";
            }

            return content;

        } catch (Exception e) {
            log.error("Failed to read file: {}", path, e);
            return "ERROR reading file '" + path + "': " + e.getMessage();
        }
    }
}
