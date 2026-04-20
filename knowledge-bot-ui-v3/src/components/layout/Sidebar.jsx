import React from 'react';
import { NavLink } from 'react-router-dom';
import { MessageSquare, Layers, BookOpen, Image, LogOut } from 'lucide-react';
import { useWorkspace } from '../../context/WorkspaceContext.jsx';
import { detachWorkspace } from '../../api/workspace.js';

const NAV_ITEMS = [
  { to: '/chat',       icon: MessageSquare, label: 'Chat'      },
  { to: '/architect',  icon: Layers,        label: 'Architect' },
  { to: '/knowledge',  icon: BookOpen,      label: 'Knowledge' },
  { to: '/visual',     icon: Image,         label: 'Visual'    },
];

export function Sidebar() {
  const { handleDetached } = useWorkspace();

  const handleDetach = async () => {
    try {
      await detachWorkspace();
    } catch (_) { /* if server is down, still clear local state */ }
    handleDetached();
  };

  return (
    <aside className="sidebar">
      <div className="sidebar-logo">🤖</div>

      <nav className="sidebar-nav">
        {NAV_ITEMS.map(({ to, icon: Icon, label }) => (
          <NavLink
            key={to}
            to={to}
            className={({ isActive }) => `sidebar-btn${isActive ? ' active' : ''}`}
            title={label}
          >
            <Icon size={18} strokeWidth={1.8} />
            <span className="sidebar-btn-label">{label}</span>
          </NavLink>
        ))}
      </nav>

      <div className="sidebar-bottom">
        <button
          className="sidebar-btn"
          onClick={handleDetach}
          title="Detach workspace"
        >
          <LogOut size={16} strokeWidth={1.8} />
          <span className="sidebar-btn-label">Detach</span>
        </button>
      </div>
    </aside>
  );
}
