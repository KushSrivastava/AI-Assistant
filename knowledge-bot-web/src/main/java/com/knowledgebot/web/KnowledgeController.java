package com.knowledgebot.web;

import com.knowledgebot.ai.knowledge.KnowledgeIngestionService;
import com.knowledgebot.ai.knowledge.KnowledgeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@RestController
@RequestMapping("/api/v1/knowledge")
@CrossOrigin
public class KnowledgeController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeController.class);

    private final KnowledgeIngestionService knowledgeIngestionService;
    private final KnowledgeProperties knowledgeProperties;

    public KnowledgeController(KnowledgeIngestionService knowledgeIngestionService,
                               KnowledgeProperties knowledgeProperties) {
        this.knowledgeIngestionService = knowledgeIngestionService;
        this.knowledgeProperties = knowledgeProperties;
    }

    @PostMapping("/upload")
    public ResponseEntity<String> uploadKnowledge(@RequestParam("file") MultipartFile file) {
        try {
            String basePath = knowledgeProperties.getBasePath();
            if (basePath.contains("${user.home}")) {
                basePath = basePath.replace("${user.home}", System.getProperty("user.home"));
            }
            
            Path knowledgePath = Paths.get(basePath);
            if (!Files.exists(knowledgePath)) {
                Files.createDirectories(knowledgePath);
            }
            
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || originalFilename.isBlank()) {
                return ResponseEntity.badRequest().body("File must have a name");
            }
            
            Path target = knowledgePath.resolve(originalFilename);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            
            // Re-index explicitly in case watcher is disabled or delayed
            knowledgeIngestionService.indexDocument(target);
            
            log.info("Knowledge document uploaded via API: {}", originalFilename);
            return ResponseEntity.ok("Document uploaded and indexed: " + originalFilename);
            
        } catch (Exception e) {
            log.error("Failed to upload knowledge document", e);
            return ResponseEntity.internalServerError().body("Upload failed: " + e.getMessage());
        }
    }
}
