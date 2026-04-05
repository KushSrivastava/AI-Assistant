package com.knowledgebot.ai.migration;

import com.knowledgebot.core.db.EntityChangeDetector;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

@Service
public class MigrationGeneratorService {

    private final ChatClient chatClient;
    private final EntityChangeDetector entityChangeDetector;

    public MigrationGeneratorService(ChatClient.Builder chatClientBuilder, EntityChangeDetector entityChangeDetector) {
        this.chatClient = chatClientBuilder.build();
        this.entityChangeDetector = entityChangeDetector;
    }

    public String generateMigration(Path entityFile) {
        String entitySchema = entityChangeDetector.extractEntitySchema(entityFile);
        
        if (entitySchema.isEmpty()) {
            return "No @Entity classes detected in the provided file.";
        }

        String prompt = "Analyze the following JPA Entity schema and generate a valid PostgreSQL Flyway migration script (.sql).\n" +
                "Include a human-readable changelog at the top explaining the DDL changes.\n\n" +
                "ENTITY SCHEMA:\n" + entitySchema;

        return chatClient.prompt()
                .system("You are an expert Database Administrator specializing in PostgreSQL and Flyway schema migrations.")
                .user(prompt)
                .call()
                .content();
    }
}
