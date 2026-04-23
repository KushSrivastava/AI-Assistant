package com.knowledgebot.web.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final AgentStatusHandler agentStatusHandler;

    public WebSocketConfig(AgentStatusHandler agentStatusHandler) {
        this.agentStatusHandler = agentStatusHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(agentStatusHandler, "/ws/agent-status")
                .setAllowedOrigins("*");
    }
}
