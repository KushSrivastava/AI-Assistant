package com.knowledgebot.web;

import com.knowledgebot.ai.model.WorkspaceManager;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;

@Component
@Order(1)
public class WorkspaceRequiredFilter implements Filter {

    private final WorkspaceManager workspaceManager;
    private static final Set<String> BYPASS_URLS = Set.of(
            "/api/v1/workspace/attach",
            "/api/v1/workspace/active",
            "/ws/agent-status"
    );

    public WorkspaceRequiredFilter(WorkspaceManager workspaceManager) {
        this.workspaceManager = workspaceManager;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String path = httpRequest.getRequestURI();

        if (path.startsWith("/api/v1/") && !isBypassed(path)) {
            if (!workspaceManager.isWorkspaceAttached()) {
                httpResponse.setStatus(428); // Precondition Required
                httpResponse.setContentType("application/json");
                httpResponse.getWriter().write("{\"error\": \"WORKSPACE_REQUIRED\", \"message\": \"You must attach a workspace before using agentic tools.\"}");
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private boolean isBypassed(String path) {
        return BYPASS_URLS.stream().anyMatch(path::equals);
    }
}
