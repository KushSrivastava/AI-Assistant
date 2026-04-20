package com.knowledgebot.ai.tools;

import com.knowledgebot.ai.model.WorkspaceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * WHY: The LLM needs to understand the project layout before creating files.
 * An agent that writes code without knowing the project structure produces incorrect
 * package names, wrong import paths, and duplicate classes.
 *
 * HOW: Walks the directory tree up to 3 levels deep and returns a tree-like
 * representation. Limited depth prevents overwhelming the context window in large projects.
 */
@Component
public class DirectoryListTool {

    private static final Logger log = LoggerFactory.getLogger(DirectoryListTool.class);
    private static final int MAX_DEPTH = 3;
    private static final int MAX_ENTRIES = 150;

    private final WorkspaceManager workspaceManager;

    public DirectoryListTool(WorkspaceManager workspaceManager) {
        this.workspaceManager = workspaceManager;
    }

    @Tool(description = """
        List all files and subdirectories at a given path in the workspace.
        Use '.' for the workspace root. Use this to explore the project structure
        BEFORE writing any new files — you must know the package structure first.
        Returns a tree-like view with file/directory icons, limited to 3 levels deep.
        """)
    public String listDirectory(
        @ToolParam(description = "Relative directory path, e.g. 'src/main/java' or '.' for workspace root")
        String path
    ) {
        try {
            if (!workspaceManager.isWorkspaceAttached()) {
                return "ERROR: No workspace attached.";
            }

            Path fullPath = workspaceManager.resolve(path);

            if (!fullPath.startsWith(workspaceManager.getActiveWorkspace())) {
                return "ERROR: Path is outside workspace boundary. Access denied.";
            }

            if (!Files.exists(fullPath)) {
                return "ERROR: Path not found: " + path;
            }

            if (!Files.isDirectory(fullPath)) {
                return "ERROR: '" + path + "' is a file, not a directory. Use readFile to read it.";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("📂 ").append(path.equals(".") ? "(workspace root)" : path).append("/\n");

            int[] count = {0};
            try (Stream<Path> walk = Files.walk(fullPath, MAX_DEPTH)) {
                walk.skip(1) // skip the root itself
                    .sorted()
                    .forEach(p -> {
                        if (count[0] >= MAX_ENTRIES) return;
                        count[0]++;

                        int depth = fullPath.relativize(p).getNameCount();
                        String indent = "  ".repeat(depth);
                        String icon = Files.isDirectory(p) ? "📁" : getFileIcon(p.getFileName().toString());
                        sb.append(indent).append(icon).append(" ")
                          .append(p.getFileName()).append("\n");
                    });
            }

            if (count[0] >= MAX_ENTRIES) {
                sb.append("  ... [Truncated at ").append(MAX_ENTRIES).append(" entries. Navigate to a subdirectory for more detail.]\n");
            }

            log.debug("Listed directory: {} ({} entries)", path, count[0]);
            return sb.toString();

        } catch (IOException e) {
            log.error("Failed to list directory: {}", path, e);
            return "ERROR listing directory '" + path + "': " + e.getMessage();
        }
    }

    private String getFileIcon(String filename) {
        if (filename.endsWith(".java"))    return "☕";
        if (filename.endsWith(".xml"))     return "📋";
        if (filename.endsWith(".yml") || filename.endsWith(".yaml")) return "⚙️";
        if (filename.endsWith(".json"))    return "🔧";
        if (filename.endsWith(".md"))      return "📝";
        if (filename.endsWith(".sql"))     return "🗄️";
        if (filename.endsWith(".html"))    return "🌐";
        if (filename.endsWith(".css"))     return "🎨";
        if (filename.endsWith(".js") || filename.endsWith(".ts")) return "📜";
        if (filename.endsWith(".properties")) return "⚙️";
        return "📄";
    }
}
