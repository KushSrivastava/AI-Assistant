package com.knowledgebot.ai.agent;

import com.knowledgebot.ai.model.WorkspaceManager;
import com.knowledgebot.ai.tools.*;
import com.knowledgebot.common.dto.AgentMessage;
import com.knowledgebot.ai.memory.ProjectMemoryService;
import com.knowledgebot.ai.memory.SessionMemoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * THE AGENTIC LOOP — upgraded with MEMORY & MCP INTEGRATION
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Refactored to support Model Context Protocol (MCP). Native web search tools 
 * have been pruned in favor of dynamically injected MCP tool callbacks (e.g., Brave Search).
 */
@Service
public class AgentLoopService {

    private static final Logger log = LoggerFactory.getLogger(AgentLoopService.class);

    private static final String SYSTEM_PROMPT_TEMPLATE = """
        You are "Agent Manager" — an expert software engineer AI assistant.
        You work inside a sandboxed project workspace and can act autonomously on coding tasks.
        
        YOU HAVE ACCESS TO THESE TOOLS:
        - readFile: Read any file in the workspace
        - writeFile: Write or create files (complete content only — not append)
        - editFile: Find-and-replace text within a file (surgical edits)
        - listDirectory: Explore the project structure
        - createDirectory: Create new folders/packages
        - deleteFile: Delete a file (use sparingly)
        - runCommand: Execute shell commands (mvn, npm, git, etc.)
        - verifyBuild: Auto-detect project type and run the build (no args needed!)
        - searchCodebase: Search for text patterns across all files
        - queryKnowledge: Search the personal knowledge base for docs/guidelines
        - External MCP Tools: You may have access to external tools dynamically loaded via MCP (e.g., brave_web_search, github_repo_management). Use these when external context or web data is needed.
        
        ── WHEN TO USE TOOLS vs WHEN TO JUST TALK ─────────────────────────────
        
        ONLY use tools when the user is asking you to perform a coding task such as:
        creating files, modifying code, running commands, searching the codebase, etc.
        
        For greetings, casual questions, or general conversation (e.g. "Hi", "How are you?",
        "What can you do?"), respond naturally in plain text WITHOUT calling any tools.
        
        ── MANDATORY OPERATING RULES (apply only during coding tasks) ──────────
        
        1. EXPLORE FIRST: Before writing new files, call listDirectory('.') to understand
           the project structure and package layout.
        
        2. READ BEFORE WRITE: Always call readFile on existing files before modifying
           them. Never overwrite a file without understanding its current content.
        
        3. BUILD VERIFY LOOP (CRITICAL):
           a. After writing or modifying code files, call verifyBuild() — it auto-detects Maven/Gradle/NPM.
           b. If build FAILS: read the error, find the failing file, fix it.
           c. Call verifyBuild() again. Repeat until it returns ✅ BUILD SUCCESS.
           d. NEVER report success without a green build.
        
        4. FILE-BY-FILE: When creating multiple files, do them ONE AT A TIME.
           Write file A → run build → fix errors → write file B → run build.
        
        5. COMPLETE CODE ONLY: Write production-ready, compilable code.
           No stubs, no TODO placeholders, no pseudocode.
        
        6. FOLLOW CONVENTIONS: Read 2-3 existing source files before writing new code
           to understand the project's style and architectural patterns.
        
        7. EXPLAIN YOUR STEPS: Before each tool call, briefly say what you're doing
           and why. After the task, summarize what was created/changed.
        
        8. SANDBOX SAFE: All file paths MUST be relative to the workspace root.
           Never use absolute paths outside the workspace.
        
        ── PROJECT MEMORY (what I remember about this project) ────────────────
        %s
        
        ── RECENT CONVERSATION (what we discussed in this session) ────────────
        %s
        
        ── RESPONSE FORMAT ────────────────────────────────────────────────────
        Wrap file contents in markdown code blocks with the language specified.
        For completed coding tasks, confirm with: ✅ Task complete — [brief summary].
        For conversational replies, respond naturally without the task-complete marker.
        """;

    private static final Set<String> CONVERSATIONAL_PREFIXES = Set.of(
        "hi", "hello", "hey", "howdy", "greetings", "good morning", "good evening",
        "good afternoon", "what can you do", "who are you", "what are you",
        "thanks", "thank you", "bye", "goodbye", "ok", "okay", "cool", "great",
        "help", "what is your name"
    );

    private boolean isConversational(String message) {
        if (message == null) return false;
        String normalized = message.trim().toLowerCase().replaceAll("[^a-z0-9 ]", "");
        if (normalized.length() <= 12) return true;
        return CONVERSATIONAL_PREFIXES.stream().anyMatch(normalized::startsWith);
    }

    private final ChatClient chatClientTemplate;
    private final ChatClient.Builder chatClientBuilder;
    private final WorkspaceManager workspaceManager;
    private final SessionMemoryService sessionMemory;
    private final ProjectMemoryService projectMemory;

    public AgentLoopService(ChatClient.Builder chatClientBuilder,
                            FileReadTool fileReadTool,
                            FileWriteTool fileWriteTool,
                            FileEditTool fileEditTool,
                            DirectoryListTool directoryListTool,
                            DirectoryCreateTool directoryCreateTool,
                            FileDeleteTool fileDeleteTool,
                            CommandExecutionTool commandExecutionTool,
                            BuildVerificationTool buildVerificationTool,
                            SearchCodebaseTool searchCodebaseTool,
                            KnowledgeRetrievalTool knowledgeRetrievalTool,
                            WorkspaceManager workspaceManager,
                            SessionMemoryService sessionMemory,
                            ProjectMemoryService projectMemory,
                            ObjectProvider<SyncMcpToolCallbackProvider> mcpProvider) {

        this.chatClientBuilder = chatClientBuilder;
        this.workspaceManager = workspaceManager;
        this.sessionMemory = sessionMemory;
        this.projectMemory = projectMemory;

        List<Object> allTools = new ArrayList<>(List.of(
                fileReadTool,
                fileWriteTool,
                fileEditTool,
                directoryListTool,
                directoryCreateTool,
                fileDeleteTool,
                commandExecutionTool,
                buildVerificationTool,
                searchCodebaseTool,
                knowledgeRetrievalTool
        ));

        // Dynamically inject MCP tool callbacks if the provider is active
        mcpProvider.ifAvailable(provider -> {
            log.info("MCP Tool Callback Provider found. Merging dynamic capabilities.");
            allTools.addAll(List.of(provider.getToolCallbacks()));
        });

        this.chatClientTemplate = chatClientBuilder
            .defaultTools(allTools.toArray())
            .build();
    }

    public AgentResponse execute(String userMessage, List<AgentMessage> conversationHistory) {
        log.info("▶ Agent task: {}", userMessage.substring(0, Math.min(120, userMessage.length())));
        long start = System.currentTimeMillis();

        String projectContext = "(no workspace attached)";
        String workspacePath = null;

        if (workspaceManager.isWorkspaceAttached()) {
            workspacePath = workspaceManager.getActiveWorkspace().toString();
            List<Document> memories = projectMemory.recall(workspacePath, userMessage);
            projectContext = projectMemory.formatMemoriesForPrompt(memories);
            log.debug("Injecting {} project memories into prompt", memories.size());
        }

        String sessionContext;
        if (!conversationHistory.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            conversationHistory.forEach(m ->
                sb.append("[").append(m.role().toUpperCase()).append("]: ")
                  .append(m.content()).append("\n")
            );
            sessionContext = sb.toString();
        } else {
            sessionContext = sessionMemory.getFormattedHistory(10);
            if (sessionContext.isBlank()) {
                sessionContext = "(start of conversation)";
            }
        }

        String enhancedSystemPrompt = SYSTEM_PROMPT_TEMPLATE.formatted(
            projectContext,
            sessionContext
        );

        sessionMemory.addUserMessage(userMessage);

        try {
            ChatClient client = isConversational(userMessage)
                ? chatClientBuilder.build()
                : chatClientTemplate;

            String response = client.prompt()
                .system(enhancedSystemPrompt)
                .user(userMessage)
                .call()
                .content();

            long elapsed = System.currentTimeMillis() - start;
            log.info("✅ Agent task completed in {}ms", elapsed);

            sessionMemory.addAssistantMessage(response);

            if (workspacePath != null && !isConversational(userMessage)) {
                projectMemory.rememberTask(workspacePath, userMessage);
            }

            return new AgentResponse(response, true, null);

        } catch (Exception e) {
            log.error("❌ Agent task failed: {}", e.getMessage(), e);
            String errorMsg = "I encountered an error: " + e.getMessage()
                + "\n\nPlease check the server logs for details.";
            sessionMemory.addAssistantMessage(errorMsg);
            return new AgentResponse(errorMsg, false, e.getMessage());
        }
    }

    public Flux<String> stream(String userMessage) {
        log.info("▶ Stream agent task: {}",
            userMessage.substring(0, Math.min(80, userMessage.length())));

        String projectContext = "(no workspace attached)";
        if (workspaceManager.isWorkspaceAttached()) {
            String wp = workspaceManager.getActiveWorkspace().toString();
            List<Document> memories = projectMemory.recall(wp, userMessage);
            projectContext = projectMemory.formatMemoriesForPrompt(memories);
        }

        String sessionContext = sessionMemory.getFormattedHistory(10);
        if (sessionContext.isBlank()) sessionContext = "(start of conversation)";

        String enhancedSystemPrompt = SYSTEM_PROMPT_TEMPLATE.formatted(projectContext, sessionContext);
        sessionMemory.addUserMessage(userMessage);

        return chatClientTemplate.prompt()
            .system(enhancedSystemPrompt)
            .user(userMessage)
            .stream()
            .content()
            .doOnComplete(() -> {
                log.info("✅ Streaming task completed");
            })
            .doOnError(e -> log.error("❌ Streaming task error: {}", e.getMessage()));
    }

    public void clearSession() {
        sessionMemory.clear();
        log.info("Session memory cleared by user request");
    }

    public record AgentResponse(String content, boolean success, String error) {}
}
