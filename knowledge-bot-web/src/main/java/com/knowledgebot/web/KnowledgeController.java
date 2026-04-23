package com.knowledgebot.web;

import com.knowledgebot.ai.knowledge.KnowledgeIngestionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/knowledge")
public class KnowledgeController {

    private final KnowledgeIngestionService knowledgeIngestionService;

    @Value("${knowledge-bot.knowledge.base-path:${user.home}/.agentmanager/knowledge}")
    private String knowledgeBasePath;

    public KnowledgeController(KnowledgeIngestionService knowledgeIngestionService) {
        this.knowledgeIngestionService = knowledgeIngestionService;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadKnowledge(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
        }

        try {
            Path baseDir = Paths.get(knowledgeBasePath);
            if (!Files.exists(baseDir)) {
                Files.createDirectories(baseDir);
            }
            Path targetPath = baseDir.resolve(file.getOriginalFilename());
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            
            // Trigger asynchronous indexing
            Thread.ofVirtual().start(() -> knowledgeIngestionService.indexDocument(targetPath));
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Document uploaded and indexing started: " + file.getOriginalFilename()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
