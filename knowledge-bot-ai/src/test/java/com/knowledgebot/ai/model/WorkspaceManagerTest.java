package com.knowledgebot.ai.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link WorkspaceManager}.
 * Tests workspace attachment, path resolution, and sandbox security.
 */
@DisplayName("WorkspaceManager")
class WorkspaceManagerTest {

    private WorkspaceManager manager;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        manager = new WorkspaceManager();
    }

    @Test
    @DisplayName("Should not be attached before any workspace is set")
    void shouldNotBeAttachedInitially() {
        assertFalse(manager.isWorkspaceAttached());
        assertNull(manager.getActiveWorkspace());
    }

    @Test
    @DisplayName("Should attach to an existing directory")
    void shouldAttachToExistingDirectory() throws IOException {
        manager.attach(tempDir);
        assertTrue(manager.isWorkspaceAttached());
        assertEquals(tempDir.toAbsolutePath().normalize(), manager.getActiveWorkspace());
    }

    @Test
    @DisplayName("Should resolve relative paths against workspace root")
    void shouldResolveRelativePaths() throws IOException {
        manager.attach(tempDir);
        Path resolved = manager.resolve("src/main/java/App.java");
        assertTrue(resolved.startsWith(tempDir.toAbsolutePath()));
        assertTrue(resolved.endsWith("App.java"));
    }

    @Test
    @DisplayName("Should resolve paths absolutely when no workspace is attached")
    void shouldResolveAbsolutelyWhenNoWorkspace() {
        Path resolved = manager.resolve("some/path.txt");
        assertNotNull(resolved);
        assertTrue(resolved.isAbsolute());
    }

    @Test
    @DisplayName("Should detach workspace and report not attached")
    void shouldDetachWorkspace() throws IOException {
        manager.attach(tempDir);
        assertTrue(manager.isWorkspaceAttached());

        manager.detach();
        assertFalse(manager.isWorkspaceAttached());
        assertNull(manager.getActiveWorkspace());
    }

    @Test
    @DisplayName("Should create workspace directory if it does not exist")
    void shouldCreateNonExistentWorkspaceDirectory() throws IOException {
        Path nonExistent = tempDir.resolve("new-workspace");
        assertFalse(nonExistent.toFile().exists());

        manager.attach(nonExistent);
        assertTrue(nonExistent.toFile().exists());
        assertTrue(manager.isWorkspaceAttached());
    }
}
