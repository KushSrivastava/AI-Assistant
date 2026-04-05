package com.knowledgebot.data.config;

import org.springframework.context.annotation.Configuration;
// The actual @Bean PgVectorStore is auto-configured by Spring AI PGVector Starter provided the application.yml properties are set.
// We keep this class here for any supplementary DB-specific or initialization functions for PGVector.
@Configuration
public class VectorStoreConfig {

}
