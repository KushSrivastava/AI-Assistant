import React from 'react';
import { motion } from 'framer-motion';

export function ToolCallCard({ tool }) {
  const icons = {
    readFile: '📄', writeFile: '📝', listDirectory: '📁',
    runCommand: '⚡', webSearch: '🌐', searchCodebase: '🔍',
    editFile: '✏️', createDirectory: '📁', deleteFile: '🗑️'
  };

  const openInEditor = async (path) => {
    try {
      await fetch('http://localhost:8080/api/v1/ide/open', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          path,
          editor: localStorage.getItem('preferred-editor') || 'vscode'
        })
      });
    } catch (e) {
      console.error('Failed to open editor', e);
    }
  };

  return (
    <motion.div 
      className="tool-call-card"
      initial={{ opacity: 0, x: -10 }}
      animate={{ opacity: 1, x: 0 }}
    >
      <div className="tool-header">
        <div className="tool-header-left">
          <span className="tool-icon">{icons[tool.name] || '🔧'}</span>
          <span className="tool-name">{tool.name}</span>
        </div>
        <span className={`tool-status ${tool.success ? 'success' : 'failed'}`}>
          {tool.success ? '✅ Success' : '❌ Failed'}
        </span>
      </div>
      
      <div className="tool-args">
        {tool.arguments && Object.entries(tool.arguments).map(([key, val]) => (
          <div key={key} className="tool-arg">
            <span className="arg-key">{key}:</span>
            <span className="arg-value">{String(val).substring(0, 100)}</span>
          </div>
        ))}
      </div>

      {tool.name === 'writeFile' && tool.arguments?.path && (
        <button 
          className="open-in-editor-btn" 
          onClick={() => openInEditor(tool.arguments.path)}
        >
          📝 Open in {localStorage.getItem('preferred-editor') === 'intellij' ? 'IntelliJ' : 'VS Code'}
        </button>
      )}
    </motion.div>
  );
}
