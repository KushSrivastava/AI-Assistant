package com.knowledgebot.core.scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class WorkspaceScannerService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceScannerService.class);
    private static final Set<String> IGNORED_DIRS = Set.of(".git", "target", "build", "node_modules", ".idea", ".mvn", "dist");

    public List<Path> scanWorkspace(Path rootPath) {
        List<Path> scannedFiles = new ArrayList<>();
        try {
            Files.walkFileTree(rootPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (IGNORED_DIRS.contains(dir.getFileName().toString())) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String fileName = file.getFileName().toString();
                    if (!Files.isDirectory(file) && !fileName.startsWith(".") && isTextFile(fileName)) {
                        scannedFiles.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.error("Failed to scan workspace", e);
        }
        return scannedFiles;
    }

    private boolean isTextFile(String fileName) {
        return fileName.endsWith(".java") || fileName.endsWith(".xml")
            || fileName.endsWith(".yaml") || fileName.endsWith(".yml")
            || fileName.endsWith(".properties") || fileName.endsWith(".md")
            || fileName.endsWith(".ts") || fileName.endsWith(".js")
            || fileName.endsWith(".html") || fileName.endsWith(".css")
            || fileName.endsWith(".sql") || fileName.endsWith(".json")
            || fileName.endsWith(".txt");
    }
}
