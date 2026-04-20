import React from 'react';
import { MarkdownRenderer } from '../common/MarkdownRenderer.jsx';

export function MessageBubble({ message }) {
  const isUser = message.role === 'user';
  const isStreaming = message.streaming;

  return (
    <div className={`message-row ${isUser ? 'user' : 'agent'}`}>
      <div className={`message-avatar ${isUser ? 'user' : 'agent'}`}>
        {isUser ? '👤' : '🤖'}
      </div>
      <div className={`message-bubble ${isUser ? 'user' : 'agent'}${isStreaming ? ' streaming' : ''}`}>
        {isUser
          ? message.content
          : <MarkdownRenderer content={message.content || ''} />
        }
      </div>
    </div>
  );
}
