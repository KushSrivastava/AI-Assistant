import React, { useState } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { StartupModal } from './components/startup/StartupModal';
import { Sidebar } from './components/layout/Sidebar';
import { TopBar } from './components/layout/TopBar';
import { ChatPanel } from './components/chat/ChatPanel';
import { KnowledgePanel } from './components/knowledge/KnowledgePanel';
import { FileTree } from './components/workspace/FileTree';
import { AgentStatusPanel } from './components/agents/AgentStatusPanel';

function App() {
  const [workspaceInfo, setWorkspaceInfo] = useState(null);

  const handleAttached = (info) => {
    setWorkspaceInfo(info);
  };

  if (!workspaceInfo) {
    return <StartupModal onAttached={handleAttached} />;
  }

  return (
    <div className="app-container">
      <Sidebar />
      <main className="main-content">
        <TopBar workspacePath={workspaceInfo.path} />
        <div className="scrollable" style={{ display: 'flex', flex: 1, padding: '16px', gap: '16px' }}>
          <div style={{ display: 'flex', flexDirection: 'column', flex: 1 }}>
            <Routes>
              <Route path="/chat" element={<ChatPanel />} />
              <Route path="/knowledge" element={<KnowledgePanel />} />
              <Route path="/workspace" element={<FileTree />} />
              <Route path="*" element={<Navigate to="/chat" replace />} />
            </Routes>
          </div>
          <AgentStatusPanel />
        </div>
      </main>
    </div>
  );
}

export default App;
