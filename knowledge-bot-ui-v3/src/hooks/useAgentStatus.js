import { useState, useEffect, useRef, useCallback } from 'react';

/**
 * useAgentStatus — connects to the real WebSocket at ws://localhost:8080/ws/agent-status
 * (proxied via Vite as /ws/agent-status).
 *
 * Returns: { status: 'idle' | 'active' | 'disconnected', lastMessage }
 */
export function useAgentStatus() {
  const [status, setStatus] = useState('disconnected');
  const [lastMessage, setLastMessage] = useState(null);
  const wsRef = useRef(null);
  const reconnectTimer = useRef(null);

  const connect = useCallback(() => {
    if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) return;

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const ws = new WebSocket(`${protocol}//${window.location.host}/ws/agent-status`);
    wsRef.current = ws;

    ws.onopen = () => {
      setStatus('idle');
      clearTimeout(reconnectTimer.current);
    };

    ws.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data);
        setLastMessage(data);
        if (data.status) setStatus(data.status);
      } catch (_) {
        // non-JSON message — ignore
      }
    };

    ws.onclose = () => {
      setStatus('disconnected');
      // Reconnect after 5 seconds
      reconnectTimer.current = setTimeout(connect, 5000);
    };

    ws.onerror = () => {
      ws.close();
    };
  }, []);

  useEffect(() => {
    connect();
    return () => {
      clearTimeout(reconnectTimer.current);
      if (wsRef.current) wsRef.current.close();
    };
  }, [connect]);

  return { status, lastMessage };
}
