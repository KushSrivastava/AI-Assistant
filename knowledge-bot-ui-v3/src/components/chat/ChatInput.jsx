import React, { useRef, useCallback } from 'react';
import { Send } from 'lucide-react';

export function ChatInput({ onSend, disabled }) {
  const textareaRef = useRef(null);

  const submit = useCallback(() => {
    const val = textareaRef.current?.value?.trim();
    if (!val || disabled) return;
    onSend(val);
    textareaRef.current.value = '';
    // Reset height
    textareaRef.current.style.height = 'auto';
  }, [onSend, disabled]);

  const handleKey = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      submit();
    }
  };

  const handleInput = (e) => {
    const el = e.target;
    el.style.height = 'auto';
    el.style.height = Math.min(el.scrollHeight, 160) + 'px';
  };

  return (
    <div className="chat-input-area">
      <div className="chat-input-wrap">
        <textarea
          ref={textareaRef}
          id="chat-input"
          className="chat-textarea"
          placeholder="Ask the agent anything… (Enter to send, Shift+Enter for new line)"
          rows={1}
          onKeyDown={handleKey}
          onInput={handleInput}
          disabled={disabled}
        />
        <button
          id="chat-send-btn"
          className="chat-send-btn"
          onClick={submit}
          disabled={disabled}
          title="Send (Enter)"
        >
          <Send size={15} />
        </button>
      </div>
      <div className="chat-hint">Enter to send · Shift+Enter for new line</div>
    </div>
  );
}
