import React, { createContext, useContext, useState, useCallback, useEffect } from 'react';
import { getWorkspaceStatus } from '../api/workspace.js';

const WorkspaceContext = createContext(null);

export function WorkspaceProvider({ children }) {
  const [workspace, setWorkspace] = useState(null); // { path, editor }
  const [preferredEditor, setPreferredEditor] = useState(
    () => localStorage.getItem('preferred-editor') || 'vscode'
  );

  // Persist editor preference
  const updateEditor = useCallback((ed) => {
    setPreferredEditor(ed);
    localStorage.setItem('preferred-editor', ed);
  }, []);

  const handleAttached = useCallback((info) => {
    setWorkspace(info);
    if (info.editor) updateEditor(info.editor);
  }, [updateEditor]);

  const handleDetached = useCallback(() => {
    setWorkspace(null);
  }, []);

  // Poll workspace status every 30s to stay in sync
  useEffect(() => {
    if (!workspace) return;
    const interval = setInterval(async () => {
      try {
        const status = await getWorkspaceStatus();
        if (!status.attached) handleDetached();
      } catch (_) { /* backend unreachable */ }
    }, 30000);
    return () => clearInterval(interval);
  }, [workspace, handleDetached]);

  return (
    <WorkspaceContext.Provider value={{
      workspace,
      preferredEditor,
      isAttached: !!workspace,
      handleAttached,
      handleDetached,
      updateEditor
    }}>
      {children}
    </WorkspaceContext.Provider>
  );
}

export const useWorkspace = () => {
  const ctx = useContext(WorkspaceContext);
  if (!ctx) throw new Error('useWorkspace must be used inside WorkspaceProvider');
  return ctx;
};
