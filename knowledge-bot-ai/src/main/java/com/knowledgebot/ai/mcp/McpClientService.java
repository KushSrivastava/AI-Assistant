package com.knowledgebot.ai.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapperSupplier;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class McpClientService implements SmartInitializingSingleton {
    
    private static final Logger log = LoggerFactory.getLogger(McpClientService.class);
    
    private final McpProperties mcpProperties;
    private final Map<String, McpSyncClient> activeClients = new ConcurrentHashMap<>();

    public McpClientService(McpProperties mcpProperties) {
        this.mcpProperties = mcpProperties;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (mcpProperties.getServers() == null || mcpProperties.getServers().isEmpty()) {
            log.info("No MCP servers configured in application.yml");
            return;
        }

        mcpProperties.getServers().forEach((serverName, config) -> {
            try {
                if ("stdio".equalsIgnoreCase(config.getTransport())) {
                    ServerParameters params = ServerParameters.builder(config.getCommand())
                            .args(config.getArgs())
                            .env(config.getEnv())
                            .build();

                    StdioClientTransport transport = new StdioClientTransport(params, new JacksonMcpJsonMapperSupplier().get());
                    McpSyncClient client = McpClient.sync(transport).build();
                    
                    client.initialize();
                    activeClients.put(serverName, client);
                    log.info("Successfully initialized MCP server: {}", serverName);
                } else {
                    log.warn("Unsupported transport type '{}' for server '{}'", config.getTransport(), serverName);
                }
            } catch (Exception e) {
                log.error("Critical failure spawning stdio process for MCP server '{}': {}", serverName, e.getMessage(), e);
            }
        });

        validateTools();
    }

    public List<McpSyncClient> getActiveConnections() {
        return new ArrayList<>(activeClients.values());
    }

    private void validateTools() {
        activeClients.forEach((name, client) -> {
            var tools = client.listTools();
            if (tools == null || tools.tools() == null || tools.tools().isEmpty()) {
                log.warn("Validation Warning: MCP server '{}' connected successfully but published zero tools.", name);
            } else {
                log.info("Validation Success: MCP server '{}' registered {} external tools.", name, tools.tools().size());
            }
        });
    }
}