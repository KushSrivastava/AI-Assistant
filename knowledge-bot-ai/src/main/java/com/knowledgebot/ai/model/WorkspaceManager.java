package com.knowledgebot.ai.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class WorkspaceManager {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceManager.class);

    private final AtomicReference<Path> activeWorkspace = new AtomicReference<>();

    public void attach(Path path) throws IOException {
        Path absolutePath = path.toAbsolutePath().normalize();
        if (!Files.exists(absolutePath)) {
            log.info("Creating non-existent workspace directory: {}", absolutePath);
            Files.createDirectories(absolutePath);
        }
        activeWorkspace.set(absolutePath);
        log.info("Workspace attached: {}", absolutePath);
    }

    public Path getActiveWorkspace() {
        return activeWorkspace.get();
    }

    public boolean isWorkspaceAttached() {
        return activeWorkspace.get() != null;
    }

    public Path resolve(String relativePath) {
        Path workspace = activeWorkspace.get();
        if (workspace == null) {
            return Paths.get(relativePath).toAbsolutePath().normalize();
        }
        return workspace.resolve(relativePath).toAbsolutePath().normalize();
    }

    /**
     * Centralised sandbox boundary enforcement.
     *
     * WHY: Every file-system tool must call this before any I/O operation.
     * Having it in one place means future security improvements (e.g., allowlists)
     * only need to be made here — not in every tool class.
     *
     * @throws SecurityException if the path is outside the workspace or no workspace is set
     */
    public void validatePathInWorkspace(Path path) {
        Path workspace = activeWorkspace.get();
        if (workspace == null) {
            throw new SecurityException("No workspace attached. Attach a workspace before performing file operations.");
        }
        Path resolved = path.toAbsolutePath().normalize();
        if (!resolved.startsWith(workspace)) {
            throw new SecurityException(
                "Access denied: '" + resolved + "' is outside the workspace boundary '" + workspace + "'. "
                + "All file operations must stay within the attached workspace.");
        }
    }

    public void detach() {
        activeWorkspace.set(null);
        log.info("Workspace detached");
    }
}
