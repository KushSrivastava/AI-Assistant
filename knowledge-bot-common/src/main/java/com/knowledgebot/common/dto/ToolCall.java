package com.knowledgebot.common.dto;

import java.util.Map;

/**
 * Represents a single tool call request made by the LLM.
 *
 * @param toolName  The name of the tool being called (e.g., "readFile", "runCommand")
 * @param arguments The key-value map of arguments the LLM provided for this call
 */
public record ToolCall(String toolName, Map<String, Object> arguments) {}
