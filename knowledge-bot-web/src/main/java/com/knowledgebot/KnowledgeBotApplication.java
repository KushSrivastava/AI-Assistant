package com.knowledgebot;

import com.knowledgebot.ai.model.ModelRoutingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(ModelRoutingProperties.class)
public class KnowledgeBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(KnowledgeBotApplication.class, args);
    }
}
