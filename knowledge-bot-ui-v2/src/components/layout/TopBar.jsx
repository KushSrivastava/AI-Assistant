import React from 'react';
import { Folder } from 'lucide-react';

export function TopBar({ workspacePath }) {
  return (
    <header className="topbar">
      <div className="topbar-path">
        <Folder size={16} className="path-icon" />
        <span>{workspacePath || 'No workspace selected'}</span>
      </div>
      <div className="topbar-actions">
        <span style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '13px', color: 'var(--success)' }}>
          <span style={{ width: '8px', height: '8px', borderRadius: '50%', background: 'var(--success)', display: 'inline-block' }} />
          System Online
        </span>
      </div>
    </header>
  );
}
