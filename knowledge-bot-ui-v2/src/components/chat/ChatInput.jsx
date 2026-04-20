import React, { useState } from 'react';
import { Send } from 'lucide-react';

export function ChatInput({ onSendMessage, disabled }) {
  const [content, setContent] = useState('');

  const handleSend = () => {
    if (content.trim() && !disabled) {
      onSendMessage(content.trim());
      setContent('');
    }
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  return (
    <div className="chat-input-container">
      <div className="chat-input-wrapper">
        <textarea
          className="chat-input"
          value={content}
          onChange={(e) => setContent(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="Type your instructions..."
          disabled={disabled}
          rows={1}
        />
        <button 
          className="send-btn" 
          onClick={handleSend}
          disabled={!content.trim() || disabled}
        >
          <Send size={18} />
        </button>
      </div>
    </div>
  );
}
