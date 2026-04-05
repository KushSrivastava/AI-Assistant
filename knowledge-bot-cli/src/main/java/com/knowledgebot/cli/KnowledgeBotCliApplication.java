package com.knowledgebot.cli;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.knowledgebot"})
public class KnowledgeBotCliApplication {

    public static void main(String[] args) {
        SpringApplication.run(KnowledgeBotCliApplication.class, args);
    }
}