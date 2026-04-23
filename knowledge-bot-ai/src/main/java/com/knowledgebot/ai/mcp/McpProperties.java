package com.knowledgebot.ai.mcp;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "knowledge-bot.mcp")
public class McpProperties {

    private Map<String, ServerConfig> servers;

    public Map<String, ServerConfig> getServers() {
        return servers;
    }

    public void setServers(Map<String, ServerConfig> servers) {
        this.servers = servers;
    }

    public static class ServerConfig {
        private String transport;
        private String command;
        private List<String> args;
        private Map<String, String> env;

        public String getTransport() { return transport; }
        public void setTransport(String transport) { this.transport = transport; }

        public String getCommand() { return command; }
        public void setCommand(String command) { this.command = command; }

        public List<String> getArgs() { return args; }
        public void setArgs(List<String> args) { this.args = args; }

        public Map<String, String> getEnv() { return env; }
        public void setEnv(Map<String, String> env) { this.env = env; }
    }
}
