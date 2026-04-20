package com.knowledgebot.common.dto;

/**
 * Represents the result of executing a single tool call.
 *
 * @param toolName The name of the tool that was executed
 * @param success  Whether the tool executed without errors
 * @param output   The text output returned by the tool (or error message if failed)
 */
public record ToolResult(String toolName, boolean success, String output) {

    /** Convenience factory for successful results. */
    public static ToolResult ok(String toolName, String output) {
        return new ToolResult(toolName, true, output);
    }

    /** Convenience factory for failed results. */
    public static ToolResult error(String toolName, String errorMessage) {
        return new ToolResult(toolName, false, "ERROR: " + errorMessage);
    }
}
