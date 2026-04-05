package com.knowledgebot.ai.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class PlanFileService {

    private static final Logger log = LoggerFactory.getLogger(PlanFileService.class);
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public Path writePlanToDisk(Path targetDir, String planContent, String goal) {
        Path planFile = targetDir.resolve("PLAN.md");
        String header = """
            # Project Plan: %s
            **Generated:** %s
            **Mode:** PLAN
            ---

            """.formatted(goal, LocalDateTime.now().format(TIMESTAMP));

        try {
            Files.writeString(planFile, header + planContent, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.info("Plan written to: {}", planFile.toAbsolutePath());
            return planFile;
        } catch (IOException e) {
            log.error("Failed to write plan to {}: {}", planFile, e.getMessage());
            throw new RuntimeException("Failed to write plan file", e);
        }
    }

    public String readPlan(Path targetDir) {
        Path planFile = targetDir.resolve("PLAN.md");
        try {
            if (Files.exists(planFile)) {
                return Files.readString(planFile);
            }
            return null;
        } catch (IOException e) {
            log.error("Failed to read plan from {}: {}", planFile, e.getMessage());
            return null;
        }
    }
}
