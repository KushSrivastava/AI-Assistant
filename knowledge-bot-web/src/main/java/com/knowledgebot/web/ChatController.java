package com.knowledgebot.web;

import com.knowledgebot.ai.agent.AgentLoopService;
import com.knowledgebot.ai.agent.AgentLoopService.AgentResponse;
import com.knowledgebot.ai.model.ModelRouterService;
import com.knowledgebot.ai.model.WorkspaceManager;
import com.knowledgebot.ai.multimodal.MultimodalService;
import com.knowledgebot.common.dto.AgentMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * REST controller exposing the Knowledge Bot's AI capabilities.
 *
 * Endpoints:
 *  GET  /api/v1/chat/simple          — Legacy streaming Q&A (SSE)
 *  POST /api/v1/chat/agent           — Agentic task execution (synchronous)
 *  GET  /api/v1/chat/agent/stream    — Agentic task execution (SSE streaming)
 *  POST /api/v1/chat/ui-to-code      — Multimodal image → code
 */
@RestController
@RequestMapping("/api/v1/chat")
@CrossOrigin // Allow the React frontend on port 3000 to call this
public class ChatController {

    private final ModelRouterService modelRouterService;
    private final MultimodalService multimodalService;
    private final AgentLoopService agentLoopService;
    private final WorkspaceManager workspaceManager;

    public ChatController(ModelRouterService modelRouterService,
                          MultimodalService multimodalService,
                          AgentLoopService agentLoopService,
                          WorkspaceManager workspaceManager) {
        this.modelRouterService = modelRouterService;
        this.multimodalService = multimodalService;
        this.agentLoopService = agentLoopService;
        this.workspaceManager = workspaceManager;
    }

    // ─── Legacy: simple streaming chat ────────────────────────────────────────

    @GetMapping(value = "/simple", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> generateStream(
        @RequestParam(value = "prompt", defaultValue = "Hello! Who are you?") String prompt
    ) {
        return modelRouterService.getClientForPrompt(prompt)
            .prompt()
            .user(prompt)
            .stream()
            .content();
    }

    // ─── NEW Phase 1: Agentic task execution (synchronous) ────────────────────

    /**
     * Execute an agentic task. The LLM will autonomously use its tools
     * (readFile, writeFile, runCommand, etc.) until the task is complete.
     *
     * Request body:
     * {
     *   "prompt": "Build a Spring Boot REST API for user management"
     * }
     *
     * WHY synchronous: Simple to start with, good for tasks < 30 seconds.
     * For longer tasks, use /agent/stream.
     */
    @PostMapping(value = "/agent", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> agentExecute(
        @RequestBody Map<String, String> request
    ) {
        String prompt = request.get("prompt");

        if (prompt == null || prompt.isBlank()) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Missing 'prompt' in request body"));
        }

        if (!workspaceManager.isWorkspaceAttached()) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED)
                .body(Map.of(
                    "error", "WORKSPACE_REQUIRED",
                    "message", "Please attach a workspace before running agent tasks. "
                        + "POST /api/v1/workspace/attach with {\"path\": \"/your/project\"}"
                ));
        }

        AgentResponse response = agentLoopService.execute(prompt, List.of());

        if (response.success()) {
            return ResponseEntity.ok(Map.of(
                "response", response.content(),
                "workspace", workspaceManager.getActiveWorkspace().toString()
            ));
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "error", "Agent task failed",
                    "details", response.error() != null ? response.error() : "Unknown error",
                    "partialResponse", response.content()
                ));
        }
    }

    // ─── NEW Phase 1: Agentic task with real-time streaming (SSE) ────────────

    /**
     * Stream an agentic task response via Server-Sent Events.
     *
     * WHY SSE: Long agent tasks (writing 10+ files) can take minutes.
     * Streaming lets the UI show the LLM's thinking in real-time so the
     * user knows progress is happening.
     *
     * Usage:
     *   GET /api/v1/chat/agent/stream?prompt=Create+a+REST+API
     *
     * Connect in JS:
     *   const es = new EventSource('/api/v1/chat/agent/stream?prompt=...')
     *   es.onmessage = e => appendToChat(e.data)
     */
    @GetMapping(value = "/agent/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> agentStream(@RequestParam String prompt) {
        if (!workspaceManager.isWorkspaceAttached()) {
            return Flux.just("data: {\"error\": \"WORKSPACE_REQUIRED\"}\n\n");
        }
        return agentLoopService.stream(prompt);
    }

    // ─── NEW Phase 2: Session memory clear ("New Conversation" button) ────────

    /**
     * Clear the current conversation session memory.
     * Useful when the user wants to start fresh on a new topic without
     * switching workspaces. Project memory (PgVector) is NOT cleared.
     */
    @PostMapping(value = "/agent/clear", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> clearSession() {
        agentLoopService.clearSession();
        return ResponseEntity.ok(Map.of(
            "status", "cleared",
            "message", "Session memory cleared. Project memory (long-term) was preserved."
        ));
    }

    // ─── Multimodal: UI image → code ─────────────────────────────────────────

    @PostMapping(value = "/ui-to-code", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String analyzeUi(
        @RequestParam("image") MultipartFile image,
        @RequestParam("prompt") String prompt
    ) throws IOException {
        return multimodalService.analyzeImageToCode(image.getBytes(), image.getContentType(), prompt);
    }

    // ─── Global error handler ─────────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleException(Exception e,
        jakarta.servlet.http.HttpServletRequest request) {

        String msg = e.getLocalizedMessage();
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;

        if (msg != null && msg.contains("more system memory")) {
            msg = "Ollama Memory Error: The AI model is too large for your system's RAM. "
                + "Consolidate to llama3.2:3b is recommended.";
        } else if (msg != null && msg.contains("404 Not Found")) {
            msg = "Ollama Model Error: Model not found. "
                + "Run: ollama pull llama3.2:3b";
            status = HttpStatus.NOT_FOUND;
        }

        String acceptHeader = request.getHeader("Accept");
        if (acceptHeader != null && acceptHeader.contains(MediaType.TEXT_EVENT_STREAM_VALUE)) {
            String sseError = String.format(
                "data: {\"error\": \"AI Request Failed\", \"details\": \"%s\"}\n\n",
                msg != null ? msg.replace("\"", "\\\"") : "Unknown error");
            return ResponseEntity.status(status)
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(sseError);
        }

        return ResponseEntity.status(status)
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of(
                "error", "AI Request Failed",
                "details", msg != null ? msg : "Unknown error"
            ));
    }
}
