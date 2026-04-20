import React from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { Sidebar } from './Sidebar.jsx';
import { StatusBar } from './StatusBar.jsx';
import { ChatPage } from '../chat/ChatPage.jsx';
import { ArchitectPage } from '../architect/ArchitectPage.jsx';
import { KnowledgePage } from '../knowledge/KnowledgePage.jsx';
import { VisualToCodePage } from '../visual/VisualToCodePage.jsx';

export function AppShell() {
  return (
    <div className="app-shell">
      <Sidebar />
      <div className="main-content">
        <StatusBar />
        <Routes>
          <Route path="/chat"      element={<ChatPage />} />
          <Route path="/architect" element={<ArchitectPage />} />
          <Route path="/knowledge" element={<KnowledgePage />} />
          <Route path="/visual"    element={<VisualToCodePage />} />
          <Route path="*"          element={<Navigate to="/chat" replace />} />
        </Routes>
      </div>
    </div>
  );
}
