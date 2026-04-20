import React from 'react';

export function AgentThinking() {
  return (
    <div className="agent-thinking">
      <div className="message-avatar agent">🤖</div>
      <div className="thinking-bubble">
        <div className="thinking-dots">
          <div className="thinking-dot" />
          <div className="thinking-dot" />
          <div className="thinking-dot" />
        </div>
        <span>Agent is working…</span>
      </div>
    </div>
  );
}
