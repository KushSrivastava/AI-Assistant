package com.knowledgebot.cli;

import com.knowledgebot.ai.model.ModeManager;
import com.knowledgebot.ai.model.ModelRouterService;
import com.knowledgebot.ai.model.WorkspaceManager;
import com.knowledgebot.ai.prompt.PromptCompositionService;
import com.knowledgebot.ai.retrieval.RetrievalService;
import com.knowledgebot.ai.model.BotMode;
import com.knowledgebot.core.performance.TokenBudgetService;
import com.knowledgebot.core.scanner.WorkspaceScannerService;
import com.knowledgebot.core.security.OutputSanitizer;
import com.knowledgebot.core.security.PromptInjectionGuard;
import com.knowledgebot.ai.pipeline.EmbeddingPipeline;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.nio.file.Path;
import java.util.List;

/**
 * Handles chat (Q&A) and workspace scanning commands.
 * Extracted from the monolithic KnowledgeBotCommands.
 */
@ShellComponent
public class ChatCommands {

    private final WorkspaceScannerService scannerService;
    private final EmbeddingPipeline embeddingPipeline;
    private final RetrievalService retrievalService;
    private final PromptCompositionService promptCompositionService;
    private final ChatClient chatClient;
    private final ModeManager modeManager;
    private final ModelRouterService modelRouterService;
    private final TokenBudgetService tokenBudgetService;
    private final OutputSanitizer sanitizer;
    private final PromptInjectionGuard injectionGuard;
    private final WorkspaceManager workspaceManager;

    public ChatCommands(WorkspaceScannerService scannerService,
                        EmbeddingPipeline embeddingPipeline,
                        RetrievalService retrievalService,
                        PromptCompositionService promptCompositionService,
                        ChatClient.Builder chatClientBuilder,
                        ModeManager modeManager,
                        ModelRouterService modelRouterService,
                        TokenBudgetService tokenBudgetService,
                        OutputSanitizer sanitizer,
                        PromptInjectionGuard injectionGuard,
                        WorkspaceManager workspaceManager) {
        this.scannerService = scannerService;
        this.embeddingPipeline = embeddingPipeline;
        this.retrievalService = retrievalService;
        this.promptCompositionService = promptCompositionService;
        this.chatClient = chatClientBuilder.build();
        this.modeManager = modeManager;
        this.modelRouterService = modelRouterService;
        this.tokenBudgetService = tokenBudgetService;
        this.sanitizer = sanitizer;
        this.injectionGuard = injectionGuard;
        this.workspaceManager = workspaceManager;
    }

    @ShellMethod(key = "scan", value = "Scan and index a project workspace")
    public String scan(@ShellOption(defaultValue = ".") String path) {
        Path rootPath = workspaceManager.resolve(path);
        List<Path> scannedFiles = scannerService.scanWorkspace(rootPath);
        embeddingPipeline.processAndEmbed(scannedFiles);
        return "Successfully scanned and indexed " + scannedFiles.size() + " files from " + rootPath;
    }

    @Cacheable("llmResponses")
    @ShellMethod(key = "chat", value = "Ask a question about the indexed workspace (ASK mode)")
    public String chat(@ShellOption String query) {
        if (modeManager.isReadOnly() || modeManager.getCurrentMode() == BotMode.ASK) {
            return handleAskQuery(query);
        }
        return handleAskQuery(query);
    }

    private String handleAskQuery(String query) {
        if (!injectionGuard.isSafe(query)) {
            return "[SECURITY BLOCK] Detected prompt injection attempt. Query rejected.";
        }
        tokenBudgetService.assertBudgetAvailable();

        List<Document> contextDocs = retrievalService.search(query, 5);
        Prompt prompt = promptCompositionService.buildPrompt(query, contextDocs);

        int activeBudget = modelRouterService.getActivePromptBudget();
        if (!tokenBudgetService.isWithinBudget(prompt.getContents(), activeBudget)) {
            return "[ERROR] Context exceeds model-aware budget (" + activeBudget + " tokens). Please narrow your search.";
        }

        ChatClient routedClient = modelRouterService.getClientForPrompt(query);
        String response = routedClient.prompt(prompt).call().content();
        tokenBudgetService.addTokens(tokenBudgetService.estimateTokens(prompt.getContents() + response));
        return sanitizer.sanitize(response);
    }
}
