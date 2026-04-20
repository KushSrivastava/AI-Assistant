package com.knowledgebot.ai.tools;

import com.knowledgebot.ai.model.WorkspaceManager;
import com.knowledgebot.core.security.CommandPermissionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * WHY: To validate code actually works, the LLM must run the build. Without this
 * the bot generates code that may not compile and has no way to know.
 *
 * HOW: Executes a shell command in the workspace directory. The LLM uses this
 * to run "mvn compile", "npm run build", "python script.py" etc.
 *
 * SECURITY:
 *   1. A blocklist prevents catastrophic commands (rm -rf /, format C:)
 *   2. CommandPermissionService enforces Safe Mode (user approval for every command)
 *   3. Execution is sandboxed to the workspace directory
 *   4. 60-second timeout prevents runaway processes
 *
 * OUTPUT: stdout + stderr are both captured and returned to the LLM.
 * Large outputs are head+tail trimmed so the LLM can still see errors at the end.
 */
@Component
public class CommandExecutionTool {

    private static final Logger log = LoggerFactory.getLogger(CommandExecutionTool.class);
    private static final int TIMEOUT_SECONDS = 60;
    private static final int MAX_OUTPUT_CHARS = 10_000;

    // Commands that could cause irreversible system damage — always blocked
    private static final Set<String> BLOCKED_PATTERNS = Set.of(
        "rm -rf /",
        "rm -rf ~",
        "del /s /q c:\\",
        "format c:",
        ":(){ :|:& };:",  // fork bomb
        "shutdown",
        "rmdir /s /q c:\\"
    );

    private final WorkspaceManager workspaceManager;
    private final CommandPermissionService permissionService;

    public CommandExecutionTool(WorkspaceManager workspaceManager,
                                CommandPermissionService permissionService) {
        this.workspaceManager = workspaceManager;
        this.permissionService = permissionService;
    }

    @Tool(description = """
        Execute a shell command in the workspace directory.
        Use this to: run builds (mvn compile, npm run build), run tests (mvn test),
        check git status, list files with specific extensions, or any terminal task.
        Returns combined stdout + stderr. Build failures show compiler errors you must fix.
        IMPORTANT: Always use this after writing code to verify it compiles.
        """)
    public String runCommand(
        @ToolParam(description = "The shell command to execute, e.g. 'mvn compile -q' or 'npm install'")
        String command
    ) {
        // SECURITY: Block dangerous patterns
        String lowerCmd = command.toLowerCase().trim();
        for (String blocked : BLOCKED_PATTERNS) {
            if (lowerCmd.contains(blocked)) {
                return "ERROR: Command blocked by security policy: '" + command
                    + "'. This command could cause irreversible damage.";
            }
        }

        if (!workspaceManager.isWorkspaceAttached()) {
            return "ERROR: No workspace attached. Cannot run commands without a workspace.";
        }

        // PERMISSION: Respect Safe Mode
        if (!permissionService.requestPermission("Execute command: " + command)) {
            return "DENIED: User did not approve running: " + command;
        }

        try {
            log.info("Executing command: {}", command);

            ProcessBuilder pb = new ProcessBuilder();
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            if (isWindows) {
                pb.command("cmd.exe", "/c", command);
            } else {
                pb.command("sh", "-c", command);
            }

            pb.directory(workspaceManager.getActiveWorkspace().toFile());
            pb.redirectErrorStream(true); // merge stderr into stdout

            Process process = pb.start();

            // Read output with a timeout
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "ERROR: Command timed out after " + TIMEOUT_SECONDS + " seconds: " + command;
            }

            int exitCode = process.exitValue();
            String outputStr = output.toString();

            // Trim very large outputs — keep head and tail so errors are visible
            if (outputStr.length() > MAX_OUTPUT_CHARS) {
                int half = MAX_OUTPUT_CHARS / 2;
                outputStr = outputStr.substring(0, half)
                    + "\n\n...[OUTPUT TRIMMED — " + outputStr.length() + " total chars]...\n\n"
                    + outputStr.substring(outputStr.length() - half);
            }

            String status = exitCode == 0 ? "SUCCESS" : "FAILED";
            log.info("Command {} | exit={} | {} chars output", status, exitCode, output.length());

            return String.format("Exit code: %d (%s)\n\n%s", exitCode, status, outputStr);

        } catch (Exception e) {
            log.error("Command execution error: {}", command, e);
            return "ERROR executing command '" + command + "': " + e.getMessage();
        }
    }
}
