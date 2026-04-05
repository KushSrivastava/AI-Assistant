package com.knowledgebot.ai.review;

import com.knowledgebot.core.git.GitDiffService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

@Service
public class PRReviewService {

    private final ChatClient chatClient;
    private final GitDiffService gitDiffService;

    public PRReviewService(ChatClient.Builder chatClientBuilder, GitDiffService gitDiffService) {
        this.chatClient = chatClientBuilder.build();
        this.gitDiffService = gitDiffService;
    }

    public String reviewLocalChanges(Path repositoryPath) {
        String diff = gitDiffService.getLocalDiff(repositoryPath);
        
        if (diff.contains("No structured diff")) {
            return "Your working directory is clean. Nothing to review!";
        }

        String prompt = "Review the following Git Unified Diff.\n" +
                "Flag specific architectural concerns, code smells, or style violations.\n" +
                "Return the findings with severity CRITICAL, WARNING, or INFO.\n\n" +
                "DIFF:\n" + diff;

        return chatClient.prompt()
                .system("You are a strict, senior Technical Lead reviewing a pull request.")
                .user(prompt)
                .call()
                .content();
    }
}
