package com.knowledgebot.common.dto;

/**
 * Represents a single message in an agent conversation.
 *
 * @param role      The sender: "user", "assistant", or "tool"
 * @param content   The text content of the message
 * @param timestamp Epoch milliseconds when this message was created
 */
public record AgentMessage(String role, String content, long timestamp) {

    /** Convenience factory for user messages. */
    public static AgentMessage user(String content) {
        return new AgentMessage("user", content, System.currentTimeMillis());
    }

    /** Convenience factory for assistant messages. */
    public static AgentMessage assistant(String content) {
        return new AgentMessage("assistant", content, System.currentTimeMillis());
    }

    /** Convenience factory for tool result messages. */
    public static AgentMessage tool(String content) {
        return new AgentMessage("tool", content, System.currentTimeMillis());
    }
}
