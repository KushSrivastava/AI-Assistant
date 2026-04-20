import React, { useState } from 'react';
import { Check, ChevronRight, Loader, AlertCircle, Send } from 'lucide-react';
import { generateHld } from '../../api/architect.js';
import { generateLld } from '../../api/architect.js';
import { executeAgent } from '../../api/chat.js';
import { MarkdownRenderer } from '../common/MarkdownRenderer.jsx';

// Step state machine
const STEPS = ['goal', 'hld', 'lld', 'implementing', 'done'];

function StepIndicator({ current }) {
  const steps = [
    { key: 'goal',         label: 'Goal'    },
    { key: 'hld',          label: 'HLD'     },
    { key: 'lld',          label: 'LLD'     },
    { key: 'implementing', label: 'Implement' },
    { key: 'done',         label: 'Done'    },
  ];

  const currentIdx = STEPS.indexOf(current);

  return (
    <div className="step-indicator">
      {steps.map((step, i) => {
        const done   = i < currentIdx;
        const active = i === currentIdx;
        return (
          <React.Fragment key={step.key}>
            <div className="step">
              <div className={`step-bubble ${done ? 'done' : active ? 'active' : ''}`}>
                {done ? <Check size={12} /> : i + 1}
              </div>
              <span className={`step-label ${done ? 'done' : active ? 'active' : ''}`}>
                {step.label}
              </span>
            </div>
            {i < steps.length - 1 && (
              <div className={`step-line ${done ? 'done' : ''}`} />
            )}
          </React.Fragment>
        );
      })}
    </div>
  );
}

export function ArchitectPage() {
  const [step, setStep]       = useState('goal');
  const [goal, setGoal]       = useState('');
  const [hld, setHld]         = useState('');
  const [lld, setLld]         = useState('');
  const [implResult, setImpl] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError]     = useState('');

  const reset = () => { setStep('goal'); setGoal(''); setHld(''); setLld(''); setImpl(''); setError(''); };

  // Step 1 → generate HLD
  const handleGenerateHld = async () => {
    if (!goal.trim()) { setError('Please describe your goal.'); return; }
    setLoading(true); setError('');
    try {
      const data = await generateHld(goal.trim());
      setHld(data.hld);
      setStep('hld');
    } catch (e) {
      setError(e.message || 'Failed to generate HLD');
    } finally { setLoading(false); }
  };

  // Step 2 → generate LLD from HLD
  const handleGenerateLld = async () => {
    setLoading(true); setError('');
    try {
      const data = await generateLld(hld);
      setLld(data.lld);
      setStep('lld');
    } catch (e) {
      setError(e.message || 'Failed to generate LLD');
    } finally { setLoading(false); }
  };

  // Step 3 → send LLD to agent for implementation
  const handleImplement = async () => {
    setLoading(true); setError('');
    setStep('implementing');
    try {
      const prompt = `You are implementing the following Low-Level Design COMPLETELY. Follow every class, field, and package name exactly as specified.\n\nLOW-LEVEL DESIGN:\n---\n${lld}\n---\n\nBegin now. Create each file, run the build after each file, and fix any errors before moving on.`;
      const data = await executeAgent(prompt);
      setImpl(data.response || 'Implementation complete.');
      setStep('done');
    } catch (e) {
      setError(e.message || 'Implementation failed');
      setStep('lld');
    } finally { setLoading(false); }
  };

  return (
    <div className="page architect-page">
      <div className="page-header">
        <div>
          <div className="page-title"><Layers /> Architect Mode</div>
          <div className="page-sub">HLD → LLD → Implementation pipeline with review gates</div>
        </div>
        {step !== 'goal' && (
          <button className="btn btn-ghost btn-sm" onClick={reset}>Start Over</button>
        )}
      </div>

      <StepIndicator current={step} />

      {loading && <div className="loading-bar" style={{ flexShrink: 0 }} />}

      {error && (
        <div className="startup-error" style={{ flexShrink: 0 }}>
          <AlertCircle size={15} style={{ flexShrink: 0 }} />
          {error}
        </div>
      )}

      <div className="architect-content scrollable">

        {/* Step 0: Goal input */}
        {step === 'goal' && (
          <div className="card" style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <div style={{ fontSize: 15, fontWeight: 600 }}>Describe your goal</div>
            <div style={{ fontSize: 13, color: 'var(--text-muted)', lineHeight: 1.7 }}>
              Be as specific as possible. The more detail you provide, the better the architecture will be.
              <br />
              Example: <em style={{ color: 'var(--text-secondary)' }}>"Build a REST API for a library management system with user authentication, book catalog, loan tracking, and overdue notifications"</em>
            </div>
            <textarea
              id="architect-goal"
              className="input"
              value={goal}
              onChange={e => setGoal(e.target.value)}
              placeholder="Describe what you want to build…"
              rows={5}
              style={{ resize: 'vertical', fontFamily: 'var(--font-body)', lineHeight: 1.7 }}
            />
            <button
              className="btn btn-primary"
              onClick={handleGenerateHld}
              disabled={loading || !goal.trim()}
              style={{ alignSelf: 'flex-end', minWidth: 180 }}
            >
              {loading ? <><Loader size={14} className="spin" /> Generating HLD…</> : <>Generate HLD <ChevronRight size={14} /></>}
            </button>
          </div>
        )}

        {/* Step 1: HLD Review */}
        {step === 'hld' && (
          <>
            <div className="card" style={{ flexShrink: 0, display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 16 }}>
              <div>
                <div className="font-semibold" style={{ marginBottom: 4 }}>High-Level Design generated ✅</div>
                <div className="text-sm text-muted">Review the architecture below. When you're happy with it, click "Generate LLD" to create the low-level design.</div>
              </div>
              <button
                className="btn btn-primary"
                onClick={handleGenerateLld}
                disabled={loading}
                style={{ flexShrink: 0 }}
              >
                {loading ? <><Loader size={14} className="spin" /> Generating LLD…</> : <>Generate LLD <ChevronRight size={14} /></>}
              </button>
            </div>
            <div className="architect-doc scrollable">
              <div className="phase-badge hld">📐 High-Level Design</div>
              <MarkdownRenderer content={hld} />
            </div>
          </>
        )}

        {/* Step 2: LLD Review */}
        {step === 'lld' && (
          <>
            <div className="card" style={{ flexShrink: 0, display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 16 }}>
              <div>
                <div className="font-semibold" style={{ marginBottom: 4 }}>Low-Level Design generated ✅</div>
                <div className="text-sm text-muted">Review class designs and package structure. Click "Start Implementation" to let the agent write all the code.</div>
              </div>
              <button
                className="btn btn-primary"
                onClick={handleImplement}
                disabled={loading}
                style={{ flexShrink: 0 }}
              >
                {loading ? <><Loader size={14} className="spin" /> Starting…</> : <><Send size={14} /> Start Implementation</>}
              </button>
            </div>
            <div className="architect-doc scrollable">
              <div className="phase-badge lld">⚙️ Low-Level Design</div>
              <MarkdownRenderer content={lld} />
            </div>
          </>
        )}

        {/* Step 3: Implementing */}
        {step === 'implementing' && (
          <div className="card" style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 20, padding: 48, textAlign: 'center' }}>
            <div style={{ fontSize: 48 }}>⚙️</div>
            <div className="font-semibold" style={{ fontSize: 18 }}>Agent is implementing…</div>
            <div className="text-sm text-muted" style={{ maxWidth: 400, lineHeight: 1.7 }}>
              The agent is creating files one by one, running the build after each, and fixing any compile errors. This can take 5-15 minutes.
            </div>
            <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
              <div className="thinking-dot" />
              <div className="thinking-dot" style={{ animationDelay: '0.2s' }} />
              <div className="thinking-dot" style={{ animationDelay: '0.4s' }} />
            </div>
          </div>
        )}

        {/* Step 4: Done */}
        {step === 'done' && (
          <>
            <div className="card" style={{ flexShrink: 0, display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 16 }}>
              <div>
                <div className="font-semibold" style={{ color: 'var(--success)', marginBottom: 4 }}>✅ Implementation Complete</div>
                <div className="text-sm text-muted">The agent has finished implementing. Check the workspace for the generated files.</div>
              </div>
              <button className="btn btn-secondary btn-sm" onClick={reset}>New Project</button>
            </div>
            <div className="architect-doc scrollable">
              <div className="phase-badge impl">✅ Implementation Report</div>
              <MarkdownRenderer content={implResult} />
            </div>
          </>
        )}
      </div>
    </div>
  );
}

// Fix missing import
function Layers({ size = 20 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none"
      stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <polygon points="12 2 2 7 12 12 22 7 12 2"/>
      <polyline points="2 17 12 22 22 17"/>
      <polyline points="2 12 12 17 22 12"/>
    </svg>
  );
}
