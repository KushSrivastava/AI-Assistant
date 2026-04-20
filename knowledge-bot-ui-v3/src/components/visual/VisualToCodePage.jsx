import React, { useState, useCallback } from 'react';
import { UploadCloud, Image, Loader, AlertCircle, Copy, Check } from 'lucide-react';
import { analyzeImageToCode } from '../../api/multimodal.js';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { oneDark } from 'react-syntax-highlighter/dist/esm/styles/prism';

export function VisualToCodePage() {
  const [image, setImage]         = useState(null);   // { url, file }
  const [prompt, setPrompt]       = useState('Generate a React component that matches this UI design.');
  const [code, setCode]           = useState('');
  const [loading, setLoading]     = useState(false);
  const [error, setError]         = useState('');
  const [isDragging, setIsDragging] = useState(false);
  const [copied, setCopied]       = useState(false);

  const acceptImage = useCallback((file) => {
    if (!file || !file.type.startsWith('image/')) return;
    setImage({ url: URL.createObjectURL(file), file });
    setCode('');
    setError('');
  }, []);

  const handleDrop = useCallback((e) => {
    e.preventDefault(); setIsDragging(false);
    acceptImage(e.dataTransfer.files[0]);
  }, [acceptImage]);

  const handleGenerate = async () => {
    if (!image) { setError('Please upload an image first.'); return; }
    setLoading(true); setError('');
    try {
      const result = await analyzeImageToCode(image.file, prompt);
      setCode(result);
    } catch (e) {
      setError(e.message || 'Failed to generate code');
    } finally { setLoading(false); }
  };

  const handleCopy = async () => {
    await navigator.clipboard.writeText(code);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  // Detect language from code
  const detectLang = (c) => {
    if (c.includes('import React') || c.includes('jsx')) return 'jsx';
    if (c.includes('<template>')) return 'html';
    if (c.includes('def ') || c.includes('import ')) return 'python';
    if (c.includes('public class') || c.includes('@Component')) return 'java';
    return 'typescript';
  };

  return (
    <div className="page" style={{ gap: 12 }}>
      <div className="page-header">
        <div>
          <div className="page-title"><Image size={18} /> Visual → Code</div>
          <div className="page-sub">Upload a UI screenshot or wireframe and get instant code</div>
        </div>
        {image && !loading && (
          <button className="btn btn-primary" onClick={handleGenerate} disabled={loading}>
            {loading ? <><Loader size={14} style={{ animation: 'spin 0.7s linear infinite' }} /> Analyzing…</> : '✨ Generate Code'}
          </button>
        )}
      </div>

      {error && (
        <div className="startup-error" style={{ flexShrink: 0 }}>
          <AlertCircle size={15} style={{ flexShrink: 0 }} /> {error}
        </div>
      )}

      {/* Prompt bar */}
      <div style={{ display: 'flex', gap: 10, flexShrink: 0 }}>
        <input
          className="input"
          value={prompt}
          onChange={e => setPrompt(e.target.value)}
          placeholder="Describe what to build from this image…"
          style={{ flex: 1 }}
        />
        {!image && (
          <button className="btn btn-primary" onClick={handleGenerate} disabled={!image || loading}>
            ✨ Generate
          </button>
        )}
      </div>

      {/* Main area */}
      <div className="visual-page">
        {/* Left: Image */}
        <div className="visual-left">
          <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
            Input Image
          </div>
          {!image ? (
            <div
              className={`drop-zone${isDragging ? ' dragging' : ''}`}
              style={{ flex: 1, cursor: 'pointer' }}
              onDragOver={e => { e.preventDefault(); setIsDragging(true); }}
              onDragLeave={() => setIsDragging(false)}
              onDrop={handleDrop}
              onClick={() => document.getElementById('visual-file-input').click()}
            >
              <div className="drop-zone-icon"><UploadCloud size={24} /></div>
              <div style={{ fontWeight: 600, marginBottom: 4 }}>Drop image here</div>
              <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>PNG, JPG, WEBP</div>
              <input
                id="visual-file-input"
                type="file"
                accept="image/*"
                onChange={e => acceptImage(e.target.files[0])}
                style={{ display: 'none' }}
              />
            </div>
          ) : (
            <div className="image-preview" style={{ flex: 1, position: 'relative' }}>
              <img src={image.url} alt="Uploaded UI design" />
              <button
                className="btn btn-ghost btn-sm"
                onClick={() => setImage(null)}
                style={{ position: 'absolute', top: 8, right: 8, background: 'rgba(10,10,15,0.8)' }}
              >
                ✕
              </button>
            </div>
          )}

          {loading && (
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, color: 'var(--text-muted)', fontSize: 13 }}>
              <Loader size={14} style={{ animation: 'spin 0.7s linear infinite', flexShrink: 0 }} />
              Analyzing image with vision model…
            </div>
          )}
        </div>

        {/* Right: Code */}
        <div className="visual-right">
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
              Generated Code
            </div>
            {code && (
              <button className="btn btn-ghost btn-sm" onClick={handleCopy}>
                {copied ? <><Check size={12} /> Copied</> : <><Copy size={12} /> Copy</>}
              </button>
            )}
          </div>

          <div className="image-preview" style={{ flex: 1, alignItems: 'flex-start', padding: code ? 0 : 24 }}>
            {code ? (
              <div style={{ width: '100%', height: '100%', overflow: 'auto' }}>
                <SyntaxHighlighter
                  language={detectLang(code)}
                  style={oneDark}
                  customStyle={{
                    margin: 0,
                    borderRadius: 'var(--radius)',
                    height: '100%',
                    fontSize: '13px',
                    background: '#0a0a16',
                  }}
                >
                  {code}
                </SyntaxHighlighter>
              </div>
            ) : (
              <div style={{ color: 'var(--text-muted)', fontSize: 13, textAlign: 'center' }}>
                {loading
                  ? 'Generating…'
                  : image
                    ? 'Click "Generate Code" above'
                    : 'Upload an image to get started'
                }
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
