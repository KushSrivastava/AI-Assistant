package com.knowledgebot.common.dto;

import java.util.List;

/**
 * Represents one complete back-and-forth turn in an agent conversation.
 *
 * A turn consists of:
 * - The user's message
 * - Zero or more tool calls made by the agent to fulfill the request
 * - The agent's final text response
 *
 * @param userMessage      The original message from the user
 * @param assistantMessage The agent's final text response
 * @param toolCalls        The list of tool calls made during this turn
 */
public record ConversationTurn(
        AgentMessage userMessage,
        AgentMessage assistantMessage,
        List<ToolCall> toolCalls
) {
    /** Create a simple turn with no tool calls. */
    public static ConversationTurn simple(AgentMessage user, AgentMessage assistant) {
        return new ConversationTurn(user, assistant, List.of());
    }
}
