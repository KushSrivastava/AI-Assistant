package com.knowledgebot.web;

import com.knowledgebot.ai.model.ModelRouterService;
import com.knowledgebot.ai.multimodal.MultimodalService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/chat")
@CrossOrigin // Allow frontend to connect from different origins (CORS)
public class ChatController {

    private final ModelRouterService modelRouterService;
    private final MultimodalService multimodalService;

    public ChatController(ModelRouterService modelRouterService, MultimodalService multimodalService) {
        this.modelRouterService = modelRouterService;
        this.multimodalService = multimodalService;
    }

    @GetMapping(value = "/simple", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> generateStream(@RequestParam(value = "prompt", defaultValue = "Hello! Who are you?") String prompt) {
        // Use the smart router to get the best client for this prompt
        return modelRouterService.getClientForPrompt(prompt)
                .prompt()
                .user(prompt)
                .stream()
                .content();
    }

    @PostMapping(value = "/ui-to-code", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String analyzeUi(@RequestParam("image") MultipartFile image, @RequestParam("prompt") String prompt) throws IOException {
        return multimodalService.analyzeImageToCode(image.getBytes(), image.getContentType(), prompt);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleException(Exception e, jakarta.servlet.http.HttpServletRequest request) {
        String msg = e.getLocalizedMessage();
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;

        if (msg != null && msg.contains("more system memory")) {
            msg = "Ollama Memory Error: The AI model is too large for your system's RAM. Consolidation to llama3.2:3b is recommended.";
        } else if (msg != null && msg.contains("404 Not Found")) {
            msg = "Ollama Model Error: The requested model was not found. Please ensure 'llama3.2:3b' is pulled (ollama pull llama3.2:3b).";
            status = HttpStatus.NOT_FOUND;
        }

        String acceptHeader = request.getHeader("Accept");
        if (acceptHeader != null && acceptHeader.contains(MediaType.TEXT_EVENT_STREAM_VALUE)) {
            // Return error in SSE format so the frontend can parse it correctly
            String sseError = String.format("data: {\"error\": \"AI Request Failed\", \"details\": \"%s\"}\n\n", 
                msg != null ? msg.replace("\"", "\\\"") : "Unknown error");
            return ResponseEntity.status(status)
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .body(sseError);
        }

        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("error", "AI Request Failed", "details", msg != null ? msg : "Unknown error"));
    }
}

