package com.knowledgebot.ai.tools;

import com.knowledgebot.ai.model.WorkspaceManager;
import com.knowledgebot.core.security.CommandPermissionService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class CommandExecutionTool {

    private final WorkspaceManager workspaceManager;
    private final CommandPermissionService permissionService;

    public CommandExecutionTool(WorkspaceManager workspaceManager, CommandPermissionService permissionService) {
        this.workspaceManager = workspaceManager;
        this.permissionService = permissionService;
    }

    @Tool(description = "Execute a shell command in the workspace directory. Use this to run builds (mvn compile), tests (mvn test), or other terminal commands. Returns stdout + stderr.")
    public String runCommand(
        @ToolParam(description = "The shell command to execute, e.g. 'mvn compile' or 'npm install'") String command
    ) {
        // SECURITY: Block explicitly dangerous commands
        if (containsDangerousCommand(command)) {
            return "ERROR: Command blocked by security policy: " + command;
        }

        if (!permissionService.requestPermission("Execute command: " + command)) {
            return "ERROR: User denied permission to execute this command.";
        }

        try {
            ProcessBuilder pb = new ProcessBuilder();
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                pb.command("cmd.exe", "/c", command);
            } else {
                pb.command("sh", "-c", command);
            }
            
            pb.directory(workspaceManager.getActiveWorkspace().toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());

            // Truncate massive outputs to prevent context overflow
            if (output.length() > 10000) {
                output = output.substring(0, 5000) + "\n\n...[TRUNCATED]...\n\n"
                       + output.substring(output.length() - 5000);
            }

            int exitCode = process.waitFor();
            return "Exit code: " + exitCode + "\n\nOutput:\n" + output;
            
        } catch (Exception e) {
            return "ERROR executing command: " + e.getMessage();
        }
    }

    private boolean containsDangerousCommand(String cmd) {
        String lower = cmd.toLowerCase();
        return lower.contains("rm -rf /") || lower.contains("format c:") ||
               lower.contains("del /s /q") || lower.contains(":(){ :|:& };:");
    }
}
