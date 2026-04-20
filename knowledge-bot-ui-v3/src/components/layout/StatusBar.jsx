import React from 'react';
import { FolderOpen } from 'lucide-react';
import { useWorkspace } from '../../context/WorkspaceContext.jsx';
import { useAgentStatus } from '../../hooks/useAgentStatus.js';

const STATUS_LABELS = {
  idle:         'Agent Idle',
  active:       'Agent Working…',
  disconnected: 'WS Disconnected',
};

export function StatusBar() {
  const { workspace } = useWorkspace();
  const { status } = useAgentStatus();

  return (
    <div className="status-bar">
      <FolderOpen size={13} style={{ color: 'var(--text-muted)', flexShrink: 0 }} />
      <span className="status-workspace" title={workspace?.path}>
        {workspace?.path || 'No workspace'}
      </span>

      <div className="status-bar-right">
        <span className="status-label">{STATUS_LABELS[status] ?? 'Unknown'}</span>
        <span className={`status-dot ${status}`} title={`WebSocket: ${status}`} />
      </div>
    </div>
  );
}
