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
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Set;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * THE AGENTIC LOOP — upgraded with MEMORY (Phase 2)
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * WHAT'S NEW IN PHASE 2:
 *
 * Before each task (RECALL):
 *   1. Load relevant project memories from PgVector (e.g., "uses Spring Boot 4")
 *   2. Load recent conversation turns from SessionMemoryService
 *   3. Inject both into the LLM system prompt as context
 *
 * After each task (LEARN):
 *   4. Record what task was completed → persist to PgVector for future sessions
 *   5. Append user message + assistant response to session window
 *
 * RESULT: The agent now knows:
 *   - What happened in the CURRENT SESSION (session memory)
 *   - What happened in PAST SESSIONS for this project (project memory / PgVector)
 *
 * Spring AI still handles the tool-calling loop internally.
 * We only add memory injection around the .call() invocation.
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
        - webSearch: Search the internet via DuckDuckGo (no API key needed)
        - readWebPage: Fetch and read the text content of a web URL
        
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

    /**
     * Short conversational messages that should never trigger tool execution.
     * Checked before running the full agentic loop.
     */
    private static final Set<String> CONVERSATIONAL_PREFIXES = Set.of(
        "hi", "hello", "hey", "howdy", "greetings", "good morning", "good evening",
        "good afternoon", "what can you do", "who are you", "what are you",
        "thanks", "thank you", "bye", "goodbye", "ok", "okay", "cool", "great",
        "help", "what is your name"
    );

    /** Returns true when the message is clearly conversational (no tools needed). */
    private boolean isConversational(String message) {
        if (message == null) return false;
        String normalized = message.trim().toLowerCase().replaceAll("[^a-z0-9 ]", "");
        // Very short messages or exact prefix match
        if (normalized.length() <= 12) return true;
        return CONVERSATIONAL_PREFIXES.stream().anyMatch(normalized::startsWith);
    }

    private final ChatClient chatClientTemplate;  // blueprint — we build per-request
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
                            WebSearchTool webSearchTool,
                            WebPageReaderTool webPageReaderTool,
                            WorkspaceManager workspaceManager,
                            SessionMemoryService sessionMemory,
                            ProjectMemoryService projectMemory) {

        this.chatClientBuilder = chatClientBuilder;
        this.workspaceManager = workspaceManager;
        this.sessionMemory = sessionMemory;
        this.projectMemory = projectMemory;

        this.chatClientTemplate = chatClientBuilder
            .defaultTools(
                fileReadTool,
                fileWriteTool,
                fileEditTool,
                directoryListTool,
                directoryCreateTool,
                fileDeleteTool,
                commandExecutionTool,
                buildVerificationTool,
                searchCodebaseTool,
                knowledgeRetrievalTool,
                webSearchTool,
                webPageReaderTool
            )
            .build();
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Execute an agentic task — synchronous, returns when complete.
     *
     * FLOW:
     *   1. Recall project memories relevant to this task (PgVector)
     *   2. Load recent session conversation history
     *   3. Build an enhanced system prompt with both memory types injected
     *   4. Run the Spring AI tool-calling loop
     *   5. Store the completed task in project memory
     *   6. Append user + assistant messages to session window
     *
     * @param userMessage         What the user wants done
     * @param conversationHistory Manually-passed history (optional — session memory is used automatically)
     */
    public AgentResponse execute(String userMessage, List<AgentMessage> conversationHistory) {
        log.info("▶ Agent task: {}", userMessage.substring(0, Math.min(120, userMessage.length())));
        long start = System.currentTimeMillis();

        // ── Step 1: Recall project-specific memories ─────────────────────────
        String projectContext = "(no workspace attached)";
        String workspacePath = null;

        if (workspaceManager.isWorkspaceAttached()) {
            workspacePath = workspaceManager.getActiveWorkspace().toString();
            List<Document> memories = projectMemory.recall(workspacePath, userMessage);
            projectContext = projectMemory.formatMemoriesForPrompt(memories);
            log.debug("Injecting {} project memories into prompt", memories.size());
        }

        // ── Step 2: Build session history context ─────────────────────────────
        // Use manually-passed history first, fall back to session memory
        String sessionContext;
        if (!conversationHistory.isEmpty()) {
            // Caller passed explicit history (e.g., from UI state)
            StringBuilder sb = new StringBuilder();
            conversationHistory.forEach(m ->
                sb.append("[").append(m.role().toUpperCase()).append("]: ")
                  .append(m.content()).append("\n")
            );
            sessionContext = sb.toString();
        } else {
            // Use the service's own session window (most common path)
            sessionContext = sessionMemory.getFormattedHistory(10);
            if (sessionContext.isBlank()) {
                sessionContext = "(start of conversation)";
            }
        }

        // ── Step 3: Build the memory-enhanced system prompt ───────────────────
        String enhancedSystemPrompt = SYSTEM_PROMPT_TEMPLATE.formatted(
            projectContext,
            sessionContext
        );

        // ── Step 4: Record user message in session window ─────────────────────
        sessionMemory.addUserMessage(userMessage);

        // ── Step 5: Run the agentic loop ──────────────────────────────────────
        try {
            // For purely conversational messages, skip tool-calling to avoid
            // the LLM calling listDirectory() before every single reply.
            ChatClient client = isConversational(userMessage)
                ? chatClientBuilder.build()  // no tools — plain conversation
                : chatClientTemplate;        // full tool-calling agent

            String response = client.prompt()
                .system(enhancedSystemPrompt)
                .user(userMessage)
                .call()
                .content();

            long elapsed = System.currentTimeMillis() - start;
            log.info("✅ Agent task completed in {}ms", elapsed);

            // ── Step 6: Store what was accomplished ───────────────────────────
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

    /**
     * Stream an agentic task response for real-time SSE delivery.
     *
     * NOTE: Memory recall and injection happen BEFORE streaming starts.
     * Tool calls during streaming still execute synchronously.
     * Only the final text answer streams token-by-token.
     */
    public Flux<String> stream(String userMessage) {
        log.info("▶ Stream agent task: {}",
            userMessage.substring(0, Math.min(80, userMessage.length())));

        // Build memory context same as execute()
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
                // Note: we can't easily capture the full streamed response here.
                // Phase 9 (UI) will accumulate chunks and call a save-memory endpoint.
            })
            .doOnError(e -> log.error("❌ Streaming task error: {}", e.getMessage()));
    }

    /**
     * Clear the current session memory (e.g., user clicks "New Conversation").
     * Project memory (PgVector) is NOT cleared — it persists across sessions.
     */
    public void clearSession() {
        sessionMemory.clear();
        log.info("Session memory cleared by user request");
    }

    /**
     * The agent's response envelope.
     *
     * @param content  The LLM's final text response
     * @param success  false only on infrastructure errors (LLM unreachable, etc.)
     * @param error    Human-readable error, null when success=true
     */
    public record AgentResponse(String content, boolean success, String error) {}
}
