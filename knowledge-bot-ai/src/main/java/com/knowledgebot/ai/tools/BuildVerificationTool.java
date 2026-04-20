package com.knowledgebot.ai.tools;

import com.knowledgebot.ai.model.WorkspaceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * WHY: The LLM sometimes needs to run the build at a higher level — check
 * "did this whole project just break?". This tool auto-detects the build system
 * and runs the appropriate command, so the LLM doesn't have to guess.
 *
 * Detection priority:
 *   1. Maven  → pom.xml exists        → mvn compile -q
 *   2. Gradle → build.gradle exists   → gradlew build (or gradle build)
 *   3. NPM    → package.json exists   → npm run build
 *   4. Python → requirements.txt      → python -m py_compile
 *
 * The LLM should call this AFTER writing all files for a feature, to
 * confirm nothing is broken before reporting success to the user.
 *
 * Differs from runCommand: This requires NO arguments — zero cognitive load
 * on the LLM to remember the build command.
 */
@Component
public class BuildVerificationTool {

    private static final Logger log = LoggerFactory.getLogger(BuildVerificationTool.class);
    private static final int TIMEOUT_SECONDS = 120; // builds can be slow

    private final WorkspaceManager workspaceManager;

    public BuildVerificationTool(WorkspaceManager workspaceManager) {
        this.workspaceManager = workspaceManager;
    }

    @Tool(description = """
        Auto-detect the project type and run the build command to verify the code compiles.
        - Maven (pom.xml) → runs 'mvn compile'
        - Gradle (build.gradle) → runs './gradlew build'
        - NPM (package.json) → runs 'npm run build'
        Use this after creating or modifying files to confirm nothing is broken.
        No arguments needed — detection is automatic.
        """)
    public String verifyBuild() {
        if (!workspaceManager.isWorkspaceAttached()) {
            return "ERROR: No workspace attached.";
        }

        Path workspace = workspaceManager.getActiveWorkspace();
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");

        // Auto-detect build system
        String command;
        String buildSystem;

        if (Files.exists(workspace.resolve("pom.xml"))) {
            buildSystem = "Maven";
            command = isWindows ? "mvn.cmd compile" : "mvn compile";
        } else if (Files.exists(workspace.resolve("build.gradle"))
                || Files.exists(workspace.resolve("build.gradle.kts"))) {
            buildSystem = "Gradle";
            command = isWindows ? "gradlew.bat build" : "./gradlew build";
        } else if (Files.exists(workspace.resolve("package.json"))) {
            buildSystem = "NPM";
            command = isWindows ? "npm.cmd run build" : "npm run build";
        } else if (Files.exists(workspace.resolve("requirements.txt"))
                || Files.exists(workspace.resolve("setup.py"))) {
            buildSystem = "Python";
            command = "python -m compileall .";
        } else {
            return "WARNING: Could not detect project type. "
                + "No pom.xml, build.gradle, or package.json found in workspace root. "
                + "Use runCommand to execute your build manually.";
        }

        log.info("▶ Build verification [{}]: {}", buildSystem, command);

        try {
            ProcessBuilder pb = new ProcessBuilder();
            if (isWindows) {
                pb.command("cmd.exe", "/c", command);
            } else {
                pb.command("sh", "-c", command);
            }
            pb.directory(workspace.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();
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
                return "ERROR: Build timed out after " + TIMEOUT_SECONDS + "s. "
                    + "The project may have unresolvable dependencies.";
            }

            int exitCode = process.exitValue();
            String result = output.toString();

            // Trim large outputs (keep start + end where the error usually is)
            if (result.length() > 8000) {
                result = result.substring(0, 3000)
                    + "\n...[OUTPUT TRIMMED]...\n"
                    + result.substring(result.length() - 3000);
            }

            if (exitCode == 0) {
                log.info("✅ Build SUCCESS [{}]", buildSystem);
                return "✅ BUILD SUCCESS [" + buildSystem + "]\n\n" + result;
            } else {
                log.warn("❌ Build FAILED [{}] exit={}", buildSystem, exitCode);
                return "❌ BUILD FAILED [" + buildSystem + "] exit=" + exitCode + "\n\n" + result
                    + "\n\nTo fix: read the error above, use readFile on the failing file, "
                    + "fix the issue, then call verifyBuild again.";
            }

        } catch (Exception e) {
            log.error("Build verification error", e);
            return "ERROR running build: " + e.getMessage()
                + "\nMake sure the build tool is on PATH.";
        }
    }
}
