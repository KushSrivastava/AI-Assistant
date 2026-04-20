import React, { useEffect, useState } from 'react';
import { RefreshCw, FileText, Folder, ChevronRight, ChevronDown } from 'lucide-react';
import { useFileTree } from '../../hooks/useFileTree';

function TreeNode({ node }) {
  const [isOpen, setIsOpen] = useState(false);

  if (!node.isDirectory) {
    return (
      <div className="tree-node">
        <FileText size={14} className="tree-node-icon" color="var(--text-secondary)" />
        {node.name}
      </div>
    );
  }

  return (
    <div>
      <div className="tree-node" onClick={() => setIsOpen(!isOpen)}>
        {isOpen ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
        <Folder size={14} className="tree-node-icon" color="var(--accent-secondary)" style={{ marginLeft: 4 }} />
        {node.name}
      </div>
      {isOpen && node.children && (
        <div className="tree-children">
          {node.children.map((child, idx) => (
            <TreeNode key={idx} node={child} />
          ))}
        </div>
      )}
    </div>
  );
}

export function FileTree() {
  const { nodes, loading, error, refreshTree } = useFileTree();

  useEffect(() => {
    refreshTree();
  }, [refreshTree]);

  return (
    <div style={{ background: 'var(--bg-secondary)', borderRadius: 'var(--radius)', border: '1px solid var(--border)', display: 'flex', flexDirection: 'column', height: '100%' }}>
      <div style={{ padding: '16px', borderBottom: '1px solid var(--border)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h2 style={{ fontSize: '16px', fontWeight: 600 }}>Workspace Explorer</h2>
        <button 
          onClick={refreshTree} 
          disabled={loading}
          style={{ background: 'transparent', border: 'none', color: 'var(--text-secondary)', cursor: loading ? 'not-allowed' : 'pointer' }}
        >
          <RefreshCw size={16} className={loading ? 'spin' : ''} />
        </button>
      </div>

      <div className="file-tree-container scrollable" style={{ flex: 1 }}>
        {error && <div style={{ color: 'var(--error)' }}>{error}</div>}
        {loading && nodes.length === 0 && <div style={{ color: 'var(--text-muted)' }}>Loading workspace...</div>}
        
        {nodes.map((node, idx) => (
          <TreeNode key={idx} node={node} />
        ))}
        
        {!loading && nodes.length === 0 && !error && (
          <div style={{ color: 'var(--text-muted)' }}>Workspace is empty</div>
        )}
      </div>
    </div>
  );
}
