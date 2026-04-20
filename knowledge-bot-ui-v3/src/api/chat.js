import { apiPost } from './client.js';

/**
 * Execute an agent task (synchronous fallback).
 * Use streamAgent() for the real-time experience.
 */
export const executeAgent = (prompt) =>
  apiPost('/chat/agent', { prompt });

/**
 * Clear the current session memory (start a new conversation).
 */
export const clearSession = () =>
  apiPost('/chat/agent/clear', {});

/**
 * Build the SSE URL for streaming agent responses.
 * Returns a URL string — the caller creates the EventSource.
 * We don't create EventSource here so the hook can manage lifecycle.
 */
export const getAgentStreamUrl = (prompt) =>
  `/api/v1/chat/agent/stream?prompt=${encodeURIComponent(prompt)}`;
