import React, { useEffect, useRef } from 'react';
import { MessageBubble } from './MessageBubble.jsx';
import { AgentThinking } from './AgentThinking.jsx';
import { ChatInput } from './ChatInput.jsx';
import { useAgentStream } from '../../hooks/useAgentStream.js';
import { clearSession } from '../../api/chat.js';
import { RotateCcw, AlertCircle } from 'lucide-react';

export function ChatPage() {
  const { messages, isStreaming, error, send, clearMessages } = useAgentStream();
  const bottomRef = useRef(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, isStreaming]);

  const handleNewChat = async () => {
    try { await clearSession(); } catch (_) { /* ignore */ }
    clearMessages();
  };

  return (
    <div className="chat-page">
      {/* Header */}
      <div style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        padding: '14px 20px',
        borderBottom: '1px solid var(--border)',
        flexShrink: 0,
        background: 'var(--bg-secondary)'
      }}>
        <div>
          <div className="font-semibold" style={{ fontSize: 15 }}>Agent Chat</div>
          <div className="text-muted text-xs" style={{ marginTop: 2 }}>
            SSE streaming · Auto session memory · 12 agent tools
          </div>
        </div>
        <button
          id="new-chat-btn"
          className="btn btn-ghost btn-sm"
          onClick={handleNewChat}
          disabled={isStreaming}
          title="Start a new conversation (clears session memory)"
        >
          <RotateCcw size={13} />
          New Chat
        </button>
      </div>

      {/* Error banner */}
      {error && (
        <div style={{
          background: 'var(--error-glow)',
          border: '1px solid rgba(239,68,68,0.25)',
          padding: '10px 20px',
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          fontSize: 13,
          color: 'var(--error)',
          flexShrink: 0
        }}>
          <AlertCircle size={14} />
          {error}
        </div>
      )}

      {/* Messages */}
      <div className="chat-messages scrollable">
        {messages.length === 0 && (
          <div style={{
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            height: '100%',
            gap: 12,
            color: 'var(--text-muted)',
          }}>
            <div style={{ fontSize: 48 }}>🤖</div>
            <div style={{ fontSize: 18, fontWeight: 600, color: 'var(--text-secondary)' }}>
              What shall we build today?
            </div>
            <div style={{ fontSize: 13, maxWidth: 420, textAlign: 'center', lineHeight: 1.7 }}>
              I can read files, write code, run commands, search the web, and reason about your entire codebase. I remember past sessions for your workspace.
            </div>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, marginTop: 8, justifyContent: 'center' }}>
              {[
                'Show me the project structure',
                'Add unit tests for the UserService',
                'Search for any TODO comments',
                'Explain how the agent loop works',
              ].map(hint => (
                <button
                  key={hint}
                  className="btn btn-secondary btn-sm"
                  onClick={() => send(hint)}
                  style={{ fontSize: 12 }}
                >
                  {hint}
                </button>
              ))}
            </div>
          </div>
        )}

        {messages.map(msg => (
          <MessageBubble key={msg.id} message={msg} />
        ))}

        {isStreaming && messages.every(m => !m.streaming) && <AgentThinking />}
        <div ref={bottomRef} />
      </div>

      <ChatInput onSend={send} disabled={isStreaming} />
    </div>
  );
}
