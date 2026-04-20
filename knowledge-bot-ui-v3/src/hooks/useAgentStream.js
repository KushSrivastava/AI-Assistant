import { useState, useEffect, useRef, useCallback } from 'react';
import { getAgentStreamUrl } from '../api/chat.js';
import { executeAgent } from '../api/chat.js';

/**
 * useAgentStream — streams agent response token-by-token.
 *
 * WHY fetch() instead of EventSource():
 *   The browser EventSource API silently strips ONE leading space from every
 *   SSE `data:` field (per spec). Spring AI sends tokens like `data: world`
 *   (the space is the word boundary). EventSource reduces that to `"world"`,
 *   so words concatenate without spaces → "Helloworld" instead of "Hello world".
 *
 *   Using fetch() + ReadableStream lets us read the raw bytes and parse SSE
 *   ourselves, preserving every character including leading spaces.
 */
export function useAgentStream() {
  const [messages, setMessages] = useState([]);
  const [isStreaming, setIsStreaming] = useState(false);
  const [error, setError] = useState(null);
  const abortRef = useRef(null);

  // Abort any in-flight stream on unmount
  useEffect(() => () => abortRef.current?.abort(), []);

  const send = useCallback(async (prompt) => {
    setError(null);
    setIsStreaming(true);

    // Add user message immediately
    const userMsg = { id: Date.now(), role: 'user', content: prompt };
    const agentMsgId = Date.now() + 1;

    setMessages(prev => [
      ...prev,
      userMsg,
      { id: agentMsgId, role: 'agent', content: '', streaming: true }
    ]);

    // Cancel previous request
    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;

    let accumulated = '';

    const updateAgent = (text, done = false) => {
      setMessages(prev => prev.map(m =>
        m.id === agentMsgId
          ? { ...m, content: text, streaming: !done }
          : m
      ));
    };

    try {
      // ── Manual SSE via fetch ──────────────────────────────────────────
      const response = await fetch(getAgentStreamUrl(prompt), {
        signal: controller.signal
      });

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }

      const reader = response.body.getReader();
      const decoder = new TextDecoder('utf-8');
      let lineBuffer = '';

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        // Decode chunk — `stream: true` keeps multi-byte chars intact across chunks
        lineBuffer += decoder.decode(value, { stream: true });

        // SSE messages are separated by double newline (\n\n)
        // Split on newlines and process line by line
        const lines = lineBuffer.split('\n');
        // Keep the last (possibly incomplete) line in the buffer
        lineBuffer = lines.pop() ?? '';

        for (const line of lines) {
          if (line.startsWith('data:')) {
            // ✅ KEY FIX: take EVERYTHING after 'data:' verbatim.
            // Do NOT strip the first space — it's a token word boundary sent by Spring AI.
            const chunk = line.slice(5); // 'data:' is 5 chars

            // Detect error JSON from backend
            if (chunk.trimStart().startsWith('{"error"')) {
              try {
                const parsed = JSON.parse(chunk.trim());
                setError(parsed.details || parsed.error || 'Agent error');
                break;
              } catch (_) { /* not valid JSON, treat as text */ }
            }

            accumulated += chunk;
            updateAgent(accumulated);
          }
          // Ignore comment lines (`:`) and empty lines
        }
      }

      // Flush remaining buffer
      if (lineBuffer.startsWith('data:')) {
        accumulated += lineBuffer.slice(5);
      }

      updateAgent(accumulated, true);

    } catch (err) {
      if (err.name === 'AbortError') {
        // User navigated away — not an error
        updateAgent(accumulated, true);
        return;
      }

      // ── Fallback: synchronous POST if SSE fails ───────────────────────
      try {
        const data = await executeAgent(prompt);
        accumulated = data.response || 'No response from agent.';
        updateAgent(accumulated, true);
      } catch (fallbackErr) {
        const msg = `❌ ${fallbackErr.message || 'Could not reach the backend. Is the Spring Boot app running on port 8080?'}`;
        updateAgent(msg, true);
        setError(fallbackErr.message);
      }
    } finally {
      setIsStreaming(false);
    }
  }, []);

  const clearMessages = useCallback(() => {
    abortRef.current?.abort();
    setMessages([]);
    setError(null);
  }, []);

  return { messages, isStreaming, error, send, clearMessages };
}
