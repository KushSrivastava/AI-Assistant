import React, { useState } from 'react';
import { UploadCloud, File, Trash2 } from 'lucide-react';

export function KnowledgePanel() {
  const [documents, setDocuments] = useState([
    { id: 1, name: 'Architecture_Design.pdf', status: 'indexed', size: '2.4 MB' },
    { id: 2, name: 'API_Specs.yaml', status: 'indexed', size: '150 KB' }
  ]);
  const [isDragging, setIsDragging] = useState(false);

  const handleDragOver = (e) => {
    e.preventDefault();
    setIsDragging(true);
  };

  const handleDragLeave = () => {
    setIsDragging(false);
  };

  const handleDrop = async (e) => {
    e.preventDefault();
    setIsDragging(false);
    
    if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
      const file = e.dataTransfer.files[0];
      const newDoc = {
        id: Date.now(),
        name: file.name,
        status: 'indexing...',
        size: `${(file.size / 1024).toFixed(1)} KB`
      };
      
      setDocuments(prev => [newDoc, ...prev]);

      // Mock uploading to the backend (to be implemented with real endpoint later)
      setTimeout(() => {
        setDocuments(prev => prev.map(d => d.id === newDoc.id ? { ...d, status: 'indexed' } : d));
      }, 2000);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '24px', height: '100%' }}>
      <div 
        style={{ 
          border: `2px dashed ${isDragging ? 'var(--accent-primary)' : 'var(--border)'}`, 
          borderRadius: 'var(--radius)', 
          padding: '48px', 
          textAlign: 'center',
          background: isDragging ? 'rgba(99, 102, 241, 0.05)' : 'var(--bg-secondary)',
          transition: 'all 0.2s'
        }}
        onDragOver={handleDragOver}
        onDragLeave={handleDragLeave}
        onDrop={handleDrop}
      >
        <UploadCloud size={48} color="var(--accent-secondary)" style={{ margin: '0 auto 16px' }} />
        <h3 style={{ fontSize: '18px', marginBottom: '8px' }}>Upload Knowledge Documents</h3>
        <p style={{ color: 'var(--text-secondary)', fontSize: '14px' }}>
          Drag and drop PDF, Word, Markdown, or Code files here.
        </p>
      </div>

      <div className="panel-container" style={{ padding: 0 }}>
        <h3 style={{ marginBottom: '16px', fontSize: '16px' }}>Indexed Documents</h3>
        <div className="card-grid">
          {documents.map(doc => (
            <div key={doc.id} className="card" style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
              <div style={{ background: 'var(--bg-primary)', padding: '12px', borderRadius: '8px' }}>
                <File size={24} color="var(--accent-primary)" />
              </div>
              <div style={{ flex: 1 }}>
                <div style={{ fontWeight: 500, fontSize: '14px', marginBottom: '4px' }}>{doc.name}</div>
                <div style={{ display: 'flex', gap: '12px', fontSize: '12px', color: 'var(--text-secondary)' }}>
                  <span>{doc.size}</span>
                  <span style={{ color: doc.status === 'indexed' ? 'var(--success)' : 'var(--warning)' }}>
                    • {doc.status}
                  </span>
                </div>
              </div>
              <button style={{ background: 'none', border: 'none', color: 'var(--text-muted)', cursor: 'pointer' }}>
                <Trash2 size={16} />
              </button>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
