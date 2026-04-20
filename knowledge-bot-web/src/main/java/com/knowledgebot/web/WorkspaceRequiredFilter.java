package com.knowledgebot.web;

import com.knowledgebot.ai.model.WorkspaceManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * WHY: Every API endpoint assumes a workspace is attached. Without this filter,
 * a user could hit /api/v1/chat/agent and get a confusing NPE instead of a
 * clear "please attach a workspace first" message.
 *
 * HOW: This filter runs on EVERY incoming request. If no workspace is attached
 * AND the request is not to an exempt path, it short-circuits with HTTP 428
 * (Precondition Required) and a JSON error the UI can display as a modal.
 *
 * EXEMPT PATHS (always allowed, even without workspace):
 *   /api/v1/workspace/**  — to SET the workspace
 *   /api/v1/health        — health check
 *   /api/v1/settings      — global settings
 *   /actuator/**          — Spring Boot actuator
 *   /                     — serving the HTML UI itself
 *   /static/**            — static assets (JS, CSS)
 *
 * UI INTEGRATION:
 *   When the UI receives HTTP 428 with { "error": "WORKSPACE_REQUIRED" },
 *   it should show the workspace picker modal (Phase 9 implements this).
 */
@Component
public class WorkspaceRequiredFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceRequiredFilter.class);

    /** Response body sent to the UI when no workspace is attached. */
    private static final String ERROR_BODY = """
        {"error":"WORKSPACE_REQUIRED","message":"No workspace attached. Use POST /api/v1/workspace/attach to set a workspace before making requests."}
        """.strip();

    /**
     * Paths that are allowed WITHOUT a workspace attached.
     * Anything starting with one of these strings bypasses the filter.
     */
    private static final Set<String> EXEMPT_PREFIXES = Set.of(
        "/api/v1/workspace",   // workspace management itself
        "/api/v1/health",      // health endpoint
        "/api/v1/settings",    // global settings (future)
        "/actuator",           // Spring Boot Actuator
        "/swagger-ui",         // API docs (future)
        "/v3/api-docs"         // OpenAPI spec (future)
    );

    /**
     * These exact paths are always served as static content — the HTML shell.
     */
    private static final Set<String> EXEMPT_EXACT = Set.of("/", "/index.html");

    private final WorkspaceManager workspaceManager;

    public WorkspaceRequiredFilter(WorkspaceManager workspaceManager) {
        this.workspaceManager = workspaceManager;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        // Skip filter for exempt paths
        if (isExempt(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Skip filter for static asset paths (images, CSS, JS)
        if (!path.startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Enforce workspace requirement for all /api/** paths
        if (!workspaceManager.isWorkspaceAttached()) {
            log.debug("Blocked request to {} — no workspace attached", path);
            response.setStatus(HttpStatus.PRECONDITION_REQUIRED.value()); // 428
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(ERROR_BODY);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isExempt(String path) {
        if (EXEMPT_EXACT.contains(path)) return true;
        return EXEMPT_PREFIXES.stream().anyMatch(path::startsWith);
    }
}
