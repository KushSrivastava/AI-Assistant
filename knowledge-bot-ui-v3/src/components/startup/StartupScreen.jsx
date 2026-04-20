import React, { useState } from 'react';
import { FolderOpen, AlertCircle, Loader } from 'lucide-react';
import { attachWorkspace } from '../../api/workspace.js';
import { WorkspaceRequiredError, ApiError } from '../../api/client.js';
import { useWorkspace } from '../../context/WorkspaceContext.jsx';

export function StartupScreen() {
  const { handleAttached, preferredEditor, updateEditor } = useWorkspace();
  const [path, setPath] = useState('');
  const [editor, setEditor] = useState(preferredEditor);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleLaunch = async () => {
    if (!path.trim()) { setError('Please enter a project folder path.'); return; }
    setLoading(true);
    setError('');

    try {
      const data = await attachWorkspace(path.trim());
      updateEditor(editor);
      handleAttached({ path: data.path, editor });
    } catch (err) {
      if (err instanceof WorkspaceRequiredError) {
        setError('Workspace required (unexpected 428 on attach). Please try again.');
      } else if (err instanceof ApiError) {
        setError(err.message);
      } else {
        setError('Cannot reach the Agent Manager backend on port 8080. Make sure the Spring Boot app is running.');
      }
    } finally {
      setLoading(false);
    }
  };

  const handleKey = (e) => {
    if (e.key === 'Enter') handleLaunch();
  };

  return (
    <div className="startup-screen">
      <div className="startup-bg" />

      <div className="startup-card">
        <div className="startup-logo-wrap">
          <div className="startup-logo">🤖</div>
          <div className="startup-brand">Agent <span>Manager</span></div>
          <div className="startup-tagline">Your autonomous AI co-worker</div>
        </div>

        {/* Workspace path */}
        <div className="startup-field">
          <label className="startup-label">Project Workspace</label>
          <div className="startup-path-wrap">
            <input
              id="workspace-path"
              className="input"
              type="text"
              value={path}
              onChange={e => setPath(e.target.value)}
              onKeyDown={handleKey}
              placeholder="D:\Projects\my-app  or  /home/user/projects/my-app"
              autoFocus
              disabled={loading}
              style={{ paddingRight: '40px' }}
            />
            <FolderOpen size={16} className="startup-path-icon" />
          </div>
        </div>

        {/* Editor preference */}
        <div className="startup-field">
          <label className="startup-label">Preferred Editor</label>
          <div className="editor-choices">
            <button
              className={`editor-choice${editor === 'vscode' ? ' active' : ''}`}
              onClick={() => setEditor('vscode')}
              type="button"
            >
              <span>📝</span> VS Code
            </button>
            <button
              className={`editor-choice${editor === 'intellij' ? ' active' : ''}`}
              onClick={() => setEditor('intellij')}
              type="button"
            >
              <span>☕</span> IntelliJ
            </button>
          </div>
        </div>

        {error && (
          <div className="startup-error">
            <AlertCircle size={15} style={{ flexShrink: 0 }} />
            {error}
          </div>
        )}

        <button
          id="launch-btn"
          className="btn btn-primary btn-lg w-full"
          onClick={handleLaunch}
          disabled={loading}
          style={{ marginTop: 8 }}
        >
          {loading ? <><Loader size={16} className="spin" /> Connecting…</> : '🚀 Launch Agent Manager'}
        </button>
      </div>

      <style>{`.spin { animation: spin 0.7s linear infinite; }`}</style>
    </div>
  );
}
