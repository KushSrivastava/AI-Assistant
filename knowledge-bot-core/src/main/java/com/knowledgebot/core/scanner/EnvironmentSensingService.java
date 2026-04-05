package com.knowledgebot.core.scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * Logic-adapted from OpenManus: Implements Environment & Tool Sensing.
 * Detective local tools to inform the bot's capabilities.
 */
@Service
public class EnvironmentSensingService {
    private static final Logger log = LoggerFactory.getLogger(EnvironmentSensingService.class);

    private final Map<String, String> installedTools = new HashMap<>();

    public void scanEnvironment() {
        String[] toolsToCheck = {"mvn", "docker", "node", "python", "git", "kubectl"};
        for (String tool : toolsToCheck) {
            checkTool(tool);
        }
    }

    private void checkTool(String tool) {
        try {
            Process process = new ProcessBuilder("cmd.exe", "/c", tool + " --version").start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String version = reader.readLine();
                if (version != null && !version.isEmpty()) {
                    installedTools.put(tool, version);
                    log.info("Sensed tool: {} [Version: {}]", tool, version);
                }
            }
        } catch (Exception e) {
            log.trace("Tool not found: {}", tool);
        }
    }

    public Map<String, String> getSensedTools() {
        if (installedTools.isEmpty()) {
            scanEnvironment();
        }
        return installedTools;
    }
    
    public String getCapabilitiesPrompt() {
        StringBuilder sb = new StringBuilder("LOCAL ENVIRONMENT CAPABILITIES:\n");
        getSensedTools().forEach((tool, version) -> sb.append("- ").append(tool).append(": ").append(version).append("\n"));
        return sb.toString();
    }
}
