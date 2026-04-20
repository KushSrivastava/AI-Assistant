import React, { useState, useCallback } from 'react';
import { UploadCloud, FileText, CheckCircle, XCircle, Loader, BookOpen } from 'lucide-react';
import { uploadKnowledgeDocument } from '../../api/knowledge.js';

const SUPPORTED = 'pdf, docx, doc, txt, md, csv, json, xml, yaml, yml';

export function KnowledgePage() {
  const [documents, setDocuments] = useState([]);
  const [isDragging, setIsDragging] = useState(false);

  const uploadFile = useCallback(async (file) => {
    const id = Date.now();
    const entry = {
      id,
      name: file.name,
      size: `${(file.size / 1024).toFixed(1)} KB`,
      status: 'uploading',
      message: ''
    };
    setDocuments(prev => [entry, ...prev]);

    try {
      const msg = await uploadKnowledgeDocument(file);
      setDocuments(prev => prev.map(d =>
        d.id === id ? { ...d, status: 'indexed', message: msg } : d
      ));
    } catch (err) {
      setDocuments(prev => prev.map(d =>
        d.id === id ? { ...d, status: 'error', message: err.message } : d
      ));
    }
  }, []);

  const handleDrop = useCallback(async (e) => {
    e.preventDefault();
    setIsDragging(false);
    const files = [...e.dataTransfer.files];
    for (const f of files) uploadFile(f);
  }, [uploadFile]);

  const handleFileInput = useCallback((e) => {
    const files = [...e.target.files];
    for (const f of files) uploadFile(f);
    e.target.value = '';
  }, [uploadFile]);

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <div className="page-title"><BookOpen size={18} /> Knowledge Base</div>
          <div className="page-sub">Upload documents and the agent will index them into its long-term memory</div>
        </div>
      </div>

      {/* Drop zone */}
      <div
        className={`drop-zone${isDragging ? ' dragging' : ''}`}
        onDragOver={e => { e.preventDefault(); setIsDragging(true); }}
        onDragLeave={() => setIsDragging(false)}
        onDrop={handleDrop}
        onClick={() => document.getElementById('file-upload-input').click()}
        style={{ cursor: 'pointer', flexShrink: 0 }}
      >
        <div className="drop-zone-icon">
          <UploadCloud size={24} />
        </div>
        <div style={{ fontWeight: 600, fontSize: 16, marginBottom: 6 }}>
          Drag & drop files here
        </div>
        <div style={{ color: 'var(--text-muted)', fontSize: 13, marginBottom: 10 }}>
          or click to browse
        </div>
        <div style={{ fontSize: 11, color: 'var(--text-muted)', fontFamily: 'var(--font-mono)' }}>
          Supports: {SUPPORTED}
        </div>
        <input
          id="file-upload-input"
          type="file"
          multiple
          accept=".pdf,.docx,.doc,.txt,.md,.csv,.json,.xml,.yaml,.yml"
          onChange={handleFileInput}
          style={{ display: 'none' }}
        />
      </div>

      {/* Document list */}
      {documents.length > 0 && (
        <div style={{ flex: 1, overflow: 'hidden', display: 'flex', flexDirection: 'column', gap: 12 }}>
          <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--text-secondary)' }}>
            Uploaded this session ({documents.length})
          </div>
          <div className="doc-list scrollable">
            {documents.map(doc => (
              <div key={doc.id} className="doc-item">
                <div className="doc-icon"><FileText size={16} /></div>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div className="doc-name truncate">{doc.name}</div>
                  <div className="doc-meta">
                    <span>{doc.size}</span>
                    {doc.message && <span className="truncate">{doc.message}</span>}
                  </div>
                </div>
                {doc.status === 'uploading' && (
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6, color: 'var(--text-muted)', fontSize: 12 }}>
                    <Loader size={13} className="spin" /> Indexing…
                  </div>
                )}
                {doc.status === 'indexed' && <CheckCircle size={16} style={{ color: 'var(--success)', flexShrink: 0 }} />}
                {doc.status === 'error' && <XCircle size={16} style={{ color: 'var(--error)', flexShrink: 0 }} />}
              </div>
            ))}
          </div>
        </div>
      )}

      {documents.length === 0 && (
        <div style={{ textAlign: 'center', color: 'var(--text-muted)', fontSize: 13, padding: '24px 0' }}>
          No documents uploaded yet. Upload your first document above.
        </div>
      )}

      <style>{`.spin { animation: spin 0.7s linear infinite; }`}</style>
    </div>
  );
}
