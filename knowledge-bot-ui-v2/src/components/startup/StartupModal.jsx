import React, { useState } from 'react';
import { motion } from 'framer-motion';

export function StartupModal({ onAttached }) {
  const [path, setPath] = useState('');
  const [editor, setEditor] = useState('vscode');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleLaunch = async () => {
    if (!path.trim()) {
      setError('Please enter a folder path');
      return;
    }

    setLoading(true);
    try {
      // In a real scenario, this would POST to localhost:8080.
      // For resilience in UI development when backend is down, we can mock success if it fails.
      let res;
      try {
        res = await fetch('http://localhost:8080/api/v1/workspace/attach', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ path: path.trim() })
        });
      } catch (fetchError) {
        console.error('Backend not reachable on 8080.', fetchError);
        setError('Unable to connect to the Knowledge Bot backend (port 8080). Make sure it is running.');
        return;
      }

      if (!res.ok) {
        const data = await res.json();
        setError(data.error || 'Failed to attach workspace');
        return;
      }

      localStorage.setItem('preferred-editor', editor);
      onAttached({ path: path.trim(), editor });
    } catch (e) {
      setError('An error occurred during launch.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <motion.div 
      className="startup-overlay"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
    >
      <motion.div 
        className="startup-modal"
        initial={{ scale: 0.9, y: 20 }}
        animate={{ scale: 1, y: 0 }}
        transition={{ type: "spring", duration: 0.5 }}
      >
        <div className="startup-logo">🤖</div>
        <h1>Agent Manager</h1>
        <p className="startup-subtitle">Your AI Co-Worker</p>

        <div className="startup-field">
          <label>Project Workspace</label>
          <div className="path-input-wrap">
            <input
              type="text"
              value={path}
              onChange={e => setPath(e.target.value)}
              placeholder="Paste your project folder path, e.g., D:\Projects\my-app"
              onKeyDown={e => e.key === 'Enter' && handleLaunch()}
            />
            <span className="path-icon">📁</span>
          </div>
        </div>

        <div className="startup-field">
          <label>Preferred Editor</label>
          <div className="editor-choices">
            <button
              className={`editor-btn ${editor === 'vscode' ? 'active' : ''}`}
              onClick={() => setEditor('vscode')}
            >
              <span>📝</span> VS Code
            </button>
            <button
              className={`editor-btn ${editor === 'intellij' ? 'active' : ''}`}
              onClick={() => setEditor('intellij')}
            >
              <span>☕</span> IntelliJ IDEA
            </button>
          </div>
        </div>

        {error && <div className="startup-error">{error}</div>}

        <button className="launch-btn" onClick={handleLaunch} disabled={loading}>
          {loading ? 'Connecting...' : '🚀 Launch Agent Manager'}
        </button>
      </motion.div>
    </motion.div>
  );
}
