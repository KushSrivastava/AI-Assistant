package com.knowledgebot.ai.memory;

import com.knowledgebot.common.dto.AgentMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * WHY: Without session memory, every message is completely independent.
 * The bot has zero idea what it said 30 seconds ago. Ask it to "fix the bug
 * we just discussed" and it has no idea what you mean.
 *
 * HOW: We maintain an in-memory list of all messages in the current conversation
 * window (user messages + assistant responses). Before each LLM call, we include
 * the recent history so the LLM has full conversational context.
 *
 * LIFECYCLE: Session memory lives only for the duration of the JVM process.
 * Restart the app → memory is cleared. That is intentional — it is SESSION memory.
 * Cross-session persistence is handled by {@link ProjectMemoryService}.
 *
 * MULTI-THREAD: Uses CopyOnWriteArrayList so multiple concurrent API requests
 * (from the UI) can read while the agent is appending new messages.
 *
 * TRIMMING: When the history grows beyond MAX_MESSAGES, we drop the oldest
 * messages via a sliding window to prevent token budget overflow.
 */
@Service
public class SessionMemoryService {

    private static final Logger log = LoggerFactory.getLogger(SessionMemoryService.class);

    /** Maximum messages to keep before trimming older ones. */
    private static final int MAX_MESSAGES = 50;

    /** When trimming, always keep the most recent N messages. */
    private static final int RETAIN_RECENT = 20;

    private final CopyOnWriteArrayList<AgentMessage> messages = new CopyOnWriteArrayList<>();

    // ─── Write ────────────────────────────────────────────────────────────────

    public void addUserMessage(String content) {
        messages.add(AgentMessage.user(content));
        log.debug("Session: +user ({} chars), total={}", content.length(), messages.size());
        trimIfNeeded();
    }

    public void addAssistantMessage(String content) {
        messages.add(AgentMessage.assistant(content));
        log.debug("Session: +assistant ({} chars), total={}", content.length(), messages.size());
        trimIfNeeded();
    }

    public void addToolMessage(String toolName, String result) {
        messages.add(AgentMessage.tool("[" + toolName + "]: " + result));
        trimIfNeeded();
    }

    // ─── Read ─────────────────────────────────────────────────────────────────

    /** Immutable snapshot of the current session history. */
    public List<AgentMessage> getHistory() {
        return Collections.unmodifiableList(new ArrayList<>(messages));
    }

    /**
     * Return the last {@code maxMessages} messages formatted as plain text
     * for injection into a system/user prompt.
     *
     * Example output:
     *   [USER]: What files are in this project?
     *   [ASSISTANT]: I found 12 files in src/main/java/com/knowledgebot...
     *   [USER]: Now add a UserController
     */
    public String getFormattedHistory(int maxMessages) {
        List<AgentMessage> snapshot = getHistory();
        int start = Math.max(0, snapshot.size() - maxMessages);

        StringBuilder sb = new StringBuilder();
        for (int i = start; i < snapshot.size(); i++) {
            AgentMessage msg = snapshot.get(i);
            sb.append("[").append(msg.role().toUpperCase()).append("]: ")
              .append(msg.content()).append("\n");
        }
        return sb.toString();
    }

    public int size() {
        return messages.size();
    }

    /** Wipe everything — called on workspace change or explicit "New Chat". */
    public void clear() {
        int was = messages.size();
        messages.clear();
        log.info("Session memory cleared ({} messages dropped)", was);
    }

    // ─── Private ──────────────────────────────────────────────────────────────

    private synchronized void trimIfNeeded() {
        if (messages.size() > MAX_MESSAGES) {
            int toDrop = messages.size() - RETAIN_RECENT;
            if (toDrop > 0) {
                messages.subList(0, toDrop).clear();
                log.info("Session trimmed: dropped {} oldest messages, kept {}", toDrop, messages.size());
            }
        }
    }
}
