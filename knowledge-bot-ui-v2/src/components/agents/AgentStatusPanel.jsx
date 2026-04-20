import React, { useState, useEffect } from 'react';
import { AgentCard } from './AgentCard';

export function AgentStatusPanel() {
  const [agents, setAgents] = useState([
    { id: 1, name: 'Architect', status: 'idle', currentTask: null }
  ]);

  useEffect(() => {
    // In actual implementation, we might poll or listen via a WebSocket.
    // For now, mock a status update over time.
    const interval = setInterval(() => {
      setAgents(prev => {
        const rand = Math.random();
        if (rand > 0.8) {
          return [{ id: 1, name: 'Architect', status: 'idle', currentTask: null }];
        } else if (rand > 0.4) {
          return [{ id: 1, name: 'Architect', status: 'active', currentTask: 'Researching Web...' }];
        }
        return prev;
      });
    }, 10000);

    return () => clearInterval(interval);
  }, []);

  return (
    <div style={{ width: '280px', display: 'flex', flexDirection: 'column', gap: '16px', borderLeft: '1px solid var(--border)', paddingLeft: '16px' }}>
      <h3 style={{ fontSize: '15px', fontWeight: 600, color: 'var(--text-secondary)' }}>Agents Status</h3>
      <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
        {agents.map(agent => (
          <AgentCard key={agent.id} agent={agent} />
        ))}
      </div>
    </div>
  );
}
