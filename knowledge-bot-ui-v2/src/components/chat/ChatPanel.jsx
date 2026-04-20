import React, { useState, useRef, useEffect } from 'react';
import { MessageBubble } from './MessageBubble';
import { ChatInput } from './ChatInput';
import { ToolCallCard } from './ToolCallCard';

export function ChatPanel() {
  const [messages, setMessages] = useState([
    { role: 'agent', content: 'Hello! I am your AI Co-Worker. How can I help you today?' }
  ]);
  const [loading, setLoading] = useState(false);
  const messagesEndRef = useRef(null);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  const handleSendMessage = async (content) => {
    const newMessages = [...messages, { role: 'user', content }];
    setMessages(newMessages);
    setLoading(true);

    try {
      const res = await fetch('http://localhost:8080/api/v1/chat/agent', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ prompt: content })
      });

      if (!res.ok) {
        throw new Error('Failed to fetch response from backend');
      }

      const data = await res.json();
      
      // Assume the response gives text and possibly tool calls.
      setMessages(prev => [...prev, { role: 'agent', content: data.response }]);
    } catch (e) {
      console.error('Backend connection failed on POST /api/v1/chat/agent.', e);
      setMessages(prev => [
        ...prev, 
        { 
          role: 'agent', 
          content: '⚠️ Error: Unable to connect to the backend agent server. Please make sure the Spring Boot application is running on port 8080.'
        }
      ]);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', background: 'var(--bg-secondary)', borderRadius: 'var(--radius)', border: '1px solid var(--border)' }}>
      <div className="chat-messages scrollable">
        {messages.map((msg, idx) => (
          <React.Fragment key={idx}>
            <MessageBubble message={msg} />
            {msg.toolCalls && msg.toolCalls.map((tool, tIdx) => (
              <div className="message-wrapper agent" key={`tool-${idx}-${tIdx}`}>
                <ToolCallCard tool={tool} />
              </div>
            ))}
          </React.Fragment>
        ))}
        {loading && (
          <div className="message-wrapper agent">
            <div className="message-bubble" style={{ color: 'var(--text-muted)' }}>
              Agent is thinking...
            </div>
          </div>
        )}
        <div ref={messagesEndRef} />
      </div>
      <ChatInput onSendMessage={handleSendMessage} disabled={loading} />
    </div>
  );
}
