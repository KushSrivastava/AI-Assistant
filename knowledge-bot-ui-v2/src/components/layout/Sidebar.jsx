import React from 'react';
import { NavLink } from 'react-router-dom';
import { MessageSquare, FolderTree, BookOpen, Settings } from 'lucide-react';

export function Sidebar() {
  return (
    <aside className="sidebar">
      <div className="sidebar-header">
        <span style={{ fontSize: '20px', marginRight: '8px' }}>🤖</span>
        <span>Agent Manager</span>
      </div>
      <nav className="sidebar-nav">
        <NavLink 
          to="/chat" 
          className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}
        >
          <MessageSquare size={18} />
          <span>Chat</span>
        </NavLink>
        <NavLink 
          to="/workspace" 
          className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}
        >
          <FolderTree size={18} />
          <span>Workspace</span>
        </NavLink>
        <NavLink 
          to="/knowledge" 
          className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}
        >
          <BookOpen size={18} />
          <span>Knowledge Base</span>
        </NavLink>
      </nav>
      <div style={{ flex: 1 }} />
      <nav className="sidebar-nav">
        <div className="nav-item">
          <Settings size={18} />
          <span>Settings</span>
        </div>
      </nav>
    </aside>
  );
}
