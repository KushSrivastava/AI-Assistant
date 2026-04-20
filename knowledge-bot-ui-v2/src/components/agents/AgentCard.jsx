import React from 'react';

export function AgentCard({ agent }) {
  return (
    <div style={{ background: 'var(--bg-card)', padding: '12px', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border)', display: 'flex', flexDirection: 'column', gap: '8px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <span style={{ fontWeight: 600, color: 'var(--accent-secondary)' }}>{agent.name}</span>
        <span style={{ fontSize: '12px', padding: '2px 6px', background: 'var(--bg-tertiary)', borderRadius: '4px', color: agent.status === 'idle' ? 'var(--text-muted)' : 'var(--success)' }}>
          {agent.status}
        </span>
      </div>
      {agent.currentTask && (
        <span style={{ fontSize: '13px', color: 'var(--text-secondary)' }}>
          Current Task: {agent.currentTask}
        </span>
      )}
    </div>
  );
}
