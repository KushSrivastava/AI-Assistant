import React, { useState } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { oneDark } from 'react-syntax-highlighter/dist/esm/styles/prism';
import { Copy, Check } from 'lucide-react';

function CopyButton({ code }) {
  const [copied, setCopied] = useState(false);

  const handleCopy = async () => {
    await navigator.clipboard.writeText(code);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <button className="code-copy-btn" onClick={handleCopy}>
      {copied
        ? <><Check size={12} /> Copied</>
        : <><Copy size={12} /> Copy</>
      }
    </button>
  );
}

export function MarkdownRenderer({ content }) {
  return (
    <ReactMarkdown
      remarkPlugins={[remarkGfm]}
      components={{
        code({ node, inline, className, children, ...props }) {
          const match = /language-(\w+)/.exec(className || '');
          const codeStr = String(children).replace(/\n$/, '');

          if (!inline && match) {
            return (
              <div className="code-block-wrapper">
                <div className="code-block-header">
                  <span className="code-lang">{match[1]}</span>
                  <CopyButton code={codeStr} />
                </div>
                <SyntaxHighlighter
                  style={oneDark}
                  language={match[1]}
                  PreTag="div"
                  customStyle={{
                    margin: 0,
                    borderRadius: '0 0 6px 6px',
                    border: '1px solid var(--border)',
                    borderTop: 'none',
                    fontSize: '13px',
                    padding: '14px 16px',
                    background: '#0a0a16',
                  }}
                  {...props}
                >
                  {codeStr}
                </SyntaxHighlighter>
              </div>
            );
          }
          return <code className={className} {...props}>{children}</code>;
        }
      }}
    >
      {content}
    </ReactMarkdown>
  );
}
