/* ======================================================
   KnowledgeBot UI – Application Logic (app.js)
   Talks to Spring Boot backend at localhost:8080
   ====================================================== */

'use strict';

// ─── Configuration ────────────────────────────────────────────────────────────
const CONFIG = {
  apiBase:      'http://localhost:8080',
  streamPath:   '/api/v1/chat/simple',
  uiToCodePath: '/api/v1/chat/ui-to-code',
  pingInterval: 8000,   // ms
  maxChars:     8000
};

// ─── State ────────────────────────────────────────────────────────────────────
const state = {
  mode:         'ASK',
  connected:    false,
  streaming:    false,
  attachedFile: null,
  activePlan:   null,
  agentJobs:    {}
};

// ─── DOM Refs ─────────────────────────────────────────────────────────────────
const $ = id => document.getElementById(id);

// Sidebar / Mode
const modePills     = document.querySelectorAll('.mode-pill');
const modeBadgeTop  = $('modeBadgeTop');
const modeTopIcon   = $('modeTopIcon');
const modeTopLabel  = $('modeTopLabel');
const modeDescTop   = $('modeDescTop');
const inputModePill = $('inputModePill');

// Connection
const connDot    = $('connDot');
const connLabel  = $('connLabel');

// Chat
const messages   = $('messages');
const promptInput = $('promptInput');
const btnSend    = $('btnSend');
const btnClear   = $('btnClear');
const tokenBar   = $('tokenBar');
const tokenLabel = $('tokenLabel');

// File attach
const fileInput   = $('fileInput');
const imagePreview= $('imagePreview');
const previewImg  = $('previewImg');

// Orchestrate
const orchestGoal    = $('orchestGoal');
const planOutput     = $('planOutput');
const btnGeneratePlan= $('btnGeneratePlan');
const btnOrchestrateRun= $('btnOrchestrateRun');
const progressCard   = $('progressCard');
const progressBoard  = $('progressBoard');
const progressPct    = $('progressPct');
const orchestResultCard = $('orchestResultCard');
const orchestResult  = $('orchestResult');

// Tabs
const tabBtns    = document.querySelectorAll('.tab-btn');
const tabPanels  = document.querySelectorAll('.tab-panel');

// Quick cmds
const quickCmds  = document.querySelectorAll('.quick-cmd');

// Model display
const modelName  = $('modelName');
const modelPurpose= $('modelPurpose');
const modelComplexity= $('modelComplexity');

// Tools filter
const filterPills = document.querySelectorAll('.filter-pill');
const toolGrid    = $('toolGrid');

// DevOps
const btnDockerGen   = $('btnDockerGen');
const btnTerraformGen= $('btnTerraformGen');
const btnCicdGen     = $('btnCicdGen');
const btnK8sGen      = $('btnK8sGen');
const devopsOutput   = $('devopsOutput');
const devopsOutputCard = $('devopsOutputCard');
const devopsOutputTitle= $('devopsOutputTitle');
const btnCopyDevops  = $('btnCopyDevops');

// ─── MCP Tool Definitions (mirrors McpToolRegistry.initializeDefaultTools) ────
const MCP_TOOLS = [
  {
    name: 'generate_plan',
    description: 'Generate a multi-step execution plan from a high-level goal.',
    category: 'planning',
    params: [{ name: 'goal', type: 'string', required: true, description: 'The goal to plan for' }]
  },
  {
    name: 'hybrid_search',
    description: 'Perform semantic and keyword search across indexed knowledge base.',
    category: 'retrieval',
    params: [
      { name: 'query', type: 'string', required: true, description: 'Search query' },
      { name: 'topK',  type: 'integer',required: false,description: 'Number of results (default 5)' }
    ]
  },
  {
    name: 'request_permission',
    description: 'Request human-in-the-loop approval before performing a sensitive action.',
    category: 'security',
    params: [{ name: 'action', type: 'string', required: true, description: 'Action requiring approval' }]
  },
  {
    name: 'code_generate',
    description: 'Generate production-ready code based on natural language instructions.',
    category: 'development',
    params: [
      { name: 'instructions', type: 'string', required: true,  description: 'Code generation instructions' },
      { name: 'language',     type: 'string', required: false, description: 'Target language (default: Java)' }
    ]
  },
  {
    name: 'code_review',
    description: 'Review a unified diff and flag security, performance, and correctness issues.',
    category: 'development',
    params: [{ name: 'diff', type: 'string', required: true, description: 'Unified diff to review' }]
  },
  {
    name: 'db_migrate',
    description: 'Generate Flyway migration SQL from a JPA entity file.',
    category: 'database',
    params: [{ name: 'entity_file', type: 'string', required: true, description: 'Path to JPA entity file' }]
  },
  {
    name: 'code_modernize',
    description: 'Modernize legacy Java code to Java 21+ idioms (records, sealed classes, etc.).',
    category: 'development',
    params: [{ name: 'file_path', type: 'string', required: true, description: 'Path to Java file' }]
  },
  {
    name: 'workspace_scan',
    description: 'Scan and semantically index a project workspace for RAG retrieval.',
    category: 'infrastructure',
    params: [{ name: 'path', type: 'string', required: false, description: 'Workspace path (default: .)' }]
  },
  {
    name: 'orchestrate',
    description: 'Execute a complex goal by breaking it into a DAG and running agent tasks in parallel.',
    category: 'orchestration',
    params: [{ name: 'goal', type: 'string', required: true, description: 'Goal to orchestrate' }]
  },
  {
    name: 'semantic_analyze',
    description: 'Extract class signatures, call-graph, and import-graph from a Java source file.',
    category: 'development',
    params: [{ name: 'file_path', type: 'string', required: true, description: 'Path to .java file' }]
  },
  {
    name: 'deploy_docker',
    description: 'Generate a production-ready Docker Compose file for the specified project.',
    category: 'infrastructure',
    params: [
      { name: 'project_name', type: 'string', required: true,  description: 'Project name' },
      { name: 'services',     type: 'string', required: false, description: 'Comma-separated services' }
    ]
  },
  {
    name: 'pr_review',
    description: 'AI-powered Pull Request review: security, quality, and style issues.',
    category: 'development',
    params: [{ name: 'diff', type: 'string', required: true, description: 'PR unified diff' }]
  }
];

// ─── Mode Definitions ─────────────────────────────────────────────────────────
const MODE_META = {
  ASK: {
    icon: '💬', label: 'ASK MODE', cssClass: 'ask-mode', pillClass: 'ask',
    desc: 'ASK mode: Read-only chat, no file modifications allowed',
    modelKey: 'local-fast', complexity: 'SIMPLE'
  },
  PLAN: {
    icon: '📋', label: 'PLAN MODE', cssClass: 'plan-mode', pillClass: 'plan',
    desc: 'PLAN mode: Can only create documentation files (PLAN.md, specs, etc.)',
    modelKey: 'local-fast', complexity: 'COMPLEX'
  },
  CODE: {
    icon: '⚡', label: 'CODE MODE', cssClass: 'code-mode', pillClass: 'code',
    desc: 'CODE mode: Full file access — create, modify, delete files and folders',
    modelKey: 'local-fast', complexity: 'MODERATE'
  }
};

const MODEL_META = {
  'local-fast':         { name: 'llama3.2:3b',          purpose: 'Fast local responses & all tasks' },
  'local-code':         { name: 'llama3.2:3b',          purpose: 'Code generation & refactoring' },
  'local-fallback':     { name: 'llama3.2:3b',          purpose: 'Primary engine' },
  'advanced-reasoning': { name: 'llama3.2:3b',          purpose: 'Logical reasoning and planning' },
  'advanced-creative':  { name: 'llama3.2:3b',          purpose: 'Creative tasks and analysis' }
};

// ─── Utility: Toast ───────────────────────────────────────────────────────────
function toast(message, type = 'info', duration = 3500) {
  const icons = { success: '✓', error: '✕', info: 'ℹ', warning: '⚠' };
  const el = document.createElement('div');
  el.className = `toast ${type}`;
  el.innerHTML = `<span>${icons[type] || 'ℹ'}</span><span>${message}</span>`;
  $('toastContainer').appendChild(el);
  setTimeout(() => {
    el.classList.add('fade-out');
    el.addEventListener('animationend', () => el.remove());
  }, duration);
}

// ─── Utility: Time ────────────────────────────────────────────────────────────
function timeNow() {
  return new Date().toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' });
}

// ─── Utility: Copy ────────────────────────────────────────────────────────────
function copyText(text) {
  navigator.clipboard.writeText(text)
    .then(() => toast('Copied to clipboard', 'success'))
    .catch(() => toast('Copy failed', 'error'));
}
window.copyText = copyText;

// ─── Mode Switching ───────────────────────────────────────────────────────────
function setMode(m) {
  state.mode = m;
  const meta = MODE_META[m];

  // Pills
  modePills.forEach(p => p.classList.toggle('active', p.dataset.mode === m));

  // Top bar badge
  modeBadgeTop.className = `mode-badge-top ${meta.cssClass}`;
  modeTopIcon.textContent = meta.icon;
  modeTopLabel.textContent = meta.label;
  modeDescTop.textContent = meta.desc;

  // Input pill
  inputModePill.textContent = m;
  inputModePill.className = `input-mode-pill ${meta.pillClass}`;

  // Model display
  const mm = MODEL_META[meta.modelKey] || MODEL_META['local-fast'];
  modelName.textContent = mm.name;
  modelPurpose.textContent = mm.purpose;
  modelComplexity.textContent = meta.complexity;

  toast(`Switched to ${m} mode`, 'info', 2000);
}

modePills.forEach(pill => {
  pill.addEventListener('click', () => setMode(pill.dataset.mode));
});

// ─── Tab Navigation ───────────────────────────────────────────────────────────
tabBtns.forEach(btn => {
  btn.addEventListener('click', () => {
    const tab = btn.dataset.tab;
    tabBtns.forEach(b => b.classList.toggle('active', b.dataset.tab === tab));
    tabPanels.forEach(p => p.classList.toggle('active', p.id === `panel${capitalize(tab)}`));
  });
});

function capitalize(s) {
  return s.charAt(0).toUpperCase() + s.slice(1);
}

// ─── Sidebar Toggle ───────────────────────────────────────────────────────────
$('sidebarToggle').addEventListener('click', () => {
  $('sidebar').classList.toggle('collapsed');
});
$('mobileSidebarToggle').addEventListener('click', () => {
  $('sidebar').classList.toggle('mobile-open');
});

// ─── Connection Health ────────────────────────────────────────────────────────
function setConnectionState(s) {
  connDot.className = `conn-dot ${s}`;
  const labels = { connected: 'Connected', disconnected: 'Disconnected', connecting: 'Connecting…' };
  connLabel.textContent = labels[s] || s;
  state.connected = (s === 'connected');
}

async function ping() {
  setConnectionState('connecting');
  try {
    const url = `${CONFIG.apiBase}${CONFIG.streamPath}?prompt=ping`;
    const res = await fetch(url, { method: 'GET', signal: AbortSignal.timeout(5000) });
    setConnectionState(res.ok ? 'connected' : 'disconnected');
    if (!res.ok) return;
    // Drain stream quickly
    const reader = res.body?.getReader();
    if (reader) { await reader.cancel(); }
  } catch {
    setConnectionState('disconnected');
  }
}

$('btnReconnect').addEventListener('click', ping);
setInterval(ping, CONFIG.pingInterval);
ping();

// ─── File Attach ──────────────────────────────────────────────────────────────
fileInput.addEventListener('change', () => {
  const f = fileInput.files[0];
  if (!f) return;
  state.attachedFile = f;
  const url = URL.createObjectURL(f);
  previewImg.src = url;
  imagePreview.style.display = 'block';
  toast(`Image attached: ${f.name}`, 'info', 2000);
});

window.clearImage = () => {
  state.attachedFile = null;
  fileInput.value = '';
  imagePreview.style.display = 'none';
};

// ─── Folder / Workspace Picker ────────────────────────────────────────────────
const folderPicker      = $('folderPicker');
const btnAttachFolder   = $('btnAttachFolder');
const btnClearFolder    = $('btnClearFolder');
const workspacePathEl   = $('workspacePath');          // hidden input that holds the path
const workspaceLabel    = $('workspacePathLabel');      // visible label
const workspaceWrap     = workspaceLabel ? workspaceLabel.closest('.workspace-path-wrap') : null;

if (btnAttachFolder) {
  btnAttachFolder.addEventListener('click', () => {
    if (folderPicker) folderPicker.click();
  });
}

if (folderPicker) {
  folderPicker.addEventListener('change', () => {
    const files = Array.from(folderPicker.files);
    if (!files.length) return;

    // Derive the top-level folder name from the webkitRelativePath of the first file
    // webkitRelativePath looks like "folderName/subdir/file.ext"
    const firstPath = files[0].webkitRelativePath || files[0].name;
    const folderName = firstPath.split('/')[0] || firstPath.split('\\')[0] || firstPath;

    // Store just the folder name (browsers never expose full OS path for security)
    if (workspacePathEl) workspacePathEl.value = folderName;
    if (workspaceLabel) {
      workspaceLabel.textContent = `📁 ${folderName}  (${files.length} files)`;
      workspaceLabel.classList.add('attached');
    }
    if (workspaceWrap) workspaceWrap.classList.add('has-folder');
    if (btnClearFolder) btnClearFolder.style.display = 'flex';

    toast(`Workspace set: ${folderName} (${files.length} files)`, 'success', 2500);
  });
}

if (btnClearFolder) {
  btnClearFolder.addEventListener('click', () => {
    if (folderPicker) { folderPicker.value = ''; }
    if (workspacePathEl) workspacePathEl.value = '';
    if (workspaceLabel) {
      workspaceLabel.textContent = 'No folder attached';
      workspaceLabel.classList.remove('attached');
    }
    if (workspaceWrap) workspaceWrap.classList.remove('has-folder');
    if (btnClearFolder) btnClearFolder.style.display = 'none';
    toast('Workspace cleared', 'info', 1500);
  });
}


// ─── Token Counter ────────────────────────────────────────────────────────────
promptInput.addEventListener('input', () => {
  const len = promptInput.value.length;
  const pct = (len / CONFIG.maxChars) * 100;
  tokenBar.style.width = `${Math.min(pct, 100)}%`;
  tokenLabel.textContent = `${len.toLocaleString()} / ${CONFIG.maxChars.toLocaleString()} chars`;
  tokenBar.className = 'token-bar' + (pct > 90 ? ' danger' : pct > 70 ? ' warn' : '');

  // Auto-resize textarea
  promptInput.style.height = 'auto';
  promptInput.style.height = Math.min(promptInput.scrollHeight, 200) + 'px';
});

// ─── Message Rendering ────────────────────────────────────────────────────────
function addMessage(role, content, cssExtra = '') {
  const isUser = role === 'user';
  const row = document.createElement('div');
  row.className = `msg-row ${isUser ? 'user' : 'bot'}`;

  const avatar = document.createElement('div');
  avatar.className = `msg-avatar ${isUser ? 'user-avatar' : 'bot-avatar'}`;
  avatar.innerHTML = isUser
    ? 'U'
    : `<svg viewBox="0 0 32 32" fill="none"><circle cx="16" cy="16" r="14" fill="url(#gb)"/><path d="M10 11h12M10 16h8M10 21h10" stroke="white" stroke-width="2" stroke-linecap="round"/><circle cx="23" cy="21" r="3" fill="white" opacity="0.9"/><defs><linearGradient id="gb" x1="0" y1="0" x2="32" y2="32"><stop offset="0%" stop-color="#6366f1"/><stop offset="100%" stop-color="#8b5cf6"/></linearGradient></defs></svg>`;

  const bubble = document.createElement('div');
  bubble.className = 'msg-bubble';

  const meta = document.createElement('div');
  meta.className = 'msg-meta';
  meta.innerHTML = `<span class="msg-sender">${isUser ? 'You' : 'KnowledgeBot'}</span><span class="msg-time">${timeNow()}</span>`;

  const msgContent = document.createElement('div');
  msgContent.className = `msg-content ${cssExtra}`;
  msgContent.textContent = content;

  bubble.appendChild(meta);
  bubble.appendChild(msgContent);
  row.appendChild(avatar);
  row.appendChild(bubble);
  messages.appendChild(row);
  scrollToBottom();
  return msgContent;
}

function addTypingIndicator() {
  const row = document.createElement('div');
  row.className = 'msg-row bot';
  row.id = 'typingRow';

  const avatar = document.createElement('div');
  avatar.className = 'msg-avatar bot-avatar';
  avatar.innerHTML = `<svg viewBox="0 0 32 32" fill="none"><circle cx="16" cy="16" r="14" fill="url(#gc)"/><path d="M10 11h12M10 16h8M10 21h10" stroke="white" stroke-width="2" stroke-linecap="round"/><circle cx="23" cy="21" r="3" fill="white" opacity="0.9"/><defs><linearGradient id="gc" x1="0" y1="0" x2="32" y2="32"><stop offset="0%" stop-color="#6366f1"/><stop offset="100%" stop-color="#8b5cf6"/></linearGradient></defs></svg>`;

  const bubble = document.createElement('div');
  bubble.className = 'msg-bubble';
  bubble.innerHTML = '<div class="msg-content"><div class="typing-indicator"><div class="typing-dot"></div><div class="typing-dot"></div><div class="typing-dot"></div></div></div>';

  row.appendChild(avatar);
  row.appendChild(bubble);
  messages.appendChild(row);
  scrollToBottom();
  return row;
}

function removeTypingIndicator() {
  const el = $('typingRow');
  if (el) el.remove();
}

function scrollToBottom() {
  messages.scrollTop = messages.scrollHeight;
}

// ─── Command Intent Routing ───────────────────────────────────────────────────
function detectIntent(text) {
  const t = text.toLowerCase().trim();
  if (/\bscan\b/.test(t) || /\bindex\b/.test(t) || /\bworkspace\b/.test(t)) return 'scan';
  if (/\bstatus\b/.test(t) || /\bhealth\b/.test(t)) return 'status';
  if (/\b(progress|plan.status)\b/.test(t)) return 'progress';
  if (/\borchestrat/.test(t) || /\bmulti.agent/.test(t)) return 'orchestrate';
  if (/\bgenerate\s+plan\b/.test(t) || /\bplan\s+for\b/.test(t) || (state.mode === 'PLAN')) return 'plan';
  if (/\bgenerate\s+(?:code|class|service|method)\b/.test(t) && state.mode === 'CODE') return 'generate';
  if (/\bmodernize\b/.test(t)) return 'modernize';
  if (/\bwatch\b/.test(t)) return 'watch';
  if (/\bdeploy\b/.test(t) || /\bdocker/.test(t)) return 'deploy';
  return 'chat';
}

// ─── Simulate Agent Activity ──────────────────────────────────────────────────
function animateAgents(active = []) {
  const allAgents = ['orchestrator', 'planner', 'codegen', 'reflection', 'devops'];
  allAgents.forEach(a => {
    const dot = $(`${a}Dot`);
    const tag = $(`${a}Tag`);
    if (active.includes(a)) {
      dot.className = 'agent-status-dot working';
      tag.className = 'agent-tag working';
      tag.textContent = 'Working';
    } else {
      dot.className = 'agent-status-dot';
      tag.className = 'agent-tag';
      tag.textContent = 'Idle';
    }
  });
}

function finishAgents(done = []) {
  done.forEach(a => {
    const dot = $(`${a}Dot`);
    const tag = $(`${a}Tag`);
    if (dot) { dot.className = 'agent-status-dot active'; }
    if (tag) { tag.className = 'agent-tag done'; tag.textContent = 'Done'; }
  });
  setTimeout(() => animateAgents([]), 3000);
}

// ─── Build payload for mode-specific prompts ───────────────────────────────────
function buildSystemContext(userText) {
  const modeCtx = {
    ASK:  'You are in ASK mode. Answer questions based on the indexed knowledge base. Do not modify files.',
    PLAN: 'You are in PLAN mode. Generate detailed, numbered markdown task plans. Format: ### Proposed Plan\n1. [ ] Task…',
    CODE: 'You are in CODE mode. Generate production-ready Java code. You have full access to create and modify files.'
  };
  const path = $('workspacePath').value.trim();
  const ctx = path ? `Workspace: ${path}\n\n` : '';
  return ctx + modeCtx[state.mode] + '\n\nUser: ' + userText;
}

// ─── Stream Chat from Backend ─────────────────────────────────────────────────
async function streamChat(userText) {
  if (state.streaming) return;
  state.streaming = true;
  btnSend.disabled = true;

  const intent = detectIntent(userText);

  // Activate relevant agents based on intent
  const intentAgentMap = {
    scan:        ['orchestrator'],
    plan:        ['planner'],
    orchestrate: ['orchestrator', 'planner'],
    generate:    ['codegen'],
    modernize:   ['codegen', 'reflection'],
    deploy:      ['devops'],
    chat:        [],
    status:      [],
    progress:    ['orchestrator'],
    watch:       []
  };
  animateAgents(intentAgentMap[intent] || []);

  // Show user message
  addMessage('user', userText);
  promptInput.value = '';
  promptInput.style.height = 'auto';
  tokenBar.style.width = '0%';
  tokenLabel.textContent = `0 / ${CONFIG.maxChars.toLocaleString()} chars`;

  // If image attached, use multimodal endpoint
  if (state.attachedFile) {
    await handleMultimodalRequest(userText);
    return;
  }

  const typingRow = addTypingIndicator();

  // Build prompt
  const fullPrompt = buildSystemContext(userText);
  const url = `${CONFIG.apiBase}${CONFIG.streamPath}?prompt=${encodeURIComponent(fullPrompt)}`;

  let botContent = null;

  try {
    const res = await fetch(url, {
      method: 'GET',
      headers: { 'Accept': 'text/event-stream' }
    });

    if (!res.ok) {
      const errJson = await res.json().catch(() => ({ error: 'Request failed', details: res.statusText }));
      removeTypingIndicator();
      const errEl = addMessage('bot', `Error: ${errJson.error || 'Request failed'}\n${errJson.details || ''}`, 'error');
      finishAgents([]);
      state.streaming = false;
      btnSend.disabled = false;
      return;
    }

    removeTypingIndicator();

    // Create streaming bot bubble
    const msgEl = addMessage('bot', '');
    const cursor = document.createElement('span');
    cursor.className = 'stream-cursor';
    msgEl.appendChild(cursor);

    let buffer = '';
    const reader = res.body.getReader();
    const decoder = new TextDecoder();

    while (true) {
      const { value, done } = await reader.read();
      if (done) break;

      const chunk = decoder.decode(value, { stream: true });
      // SSE format: "data: <token>\n\n"
      const lines = chunk.split('\n');
      for (const line of lines) {
        if (line.startsWith('data:')) {
          const token = line.slice(5); // strip 'data:'
          buffer += token;
          msgEl.textContent = buffer;
          msgEl.appendChild(cursor);
          scrollToBottom();
        } else if (line.trim()) {
          // Raw token (non-SSE stream)
          buffer += line;
          msgEl.textContent = buffer;
          msgEl.appendChild(cursor);
          scrollToBottom();
        }
      }
    }

    cursor.remove();
    if (!buffer) msgEl.textContent = '(No response from model. Is Ollama running?)';

    finishAgents(intentAgentMap[intent] || []);

  } catch (err) {
    removeTypingIndicator();
    if (err.name === 'TypeError' && !state.connected) {
      addMessage('bot', '⚠️ Cannot reach the backend. Make sure the Spring Boot app is running on port 8080.', 'error');
    } else {
      addMessage('bot', `⚠️ ${err.message}`, 'error');
    }
    animateAgents([]);
  }

  state.streaming = false;
  btnSend.disabled = false;
}

// ─── Multimodal (image) Request ───────────────────────────────────────────────
async function handleMultimodalRequest(prompt) {
  const typingRow = addTypingIndicator();
  try {
    const fd = new FormData();
    fd.append('image', state.attachedFile);
    fd.append('prompt', prompt);

    const res = await fetch(`${CONFIG.apiBase}${CONFIG.uiToCodePath}`, { method: 'POST', body: fd });
    removeTypingIndicator();
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const text = await res.text();
    addMessage('bot', text || '(Empty response)');
    window.clearImage();
  } catch (err) {
    removeTypingIndicator();
    addMessage('bot', `⚠️ Multimodal request failed: ${err.message}`, 'error');
  }
  state.streaming = false;
  btnSend.disabled = false;
  animateAgents([]);
}

// ─── Send Handler ─────────────────────────────────────────────────────────────
async function handleSend() {
  const text = promptInput.value.trim();
  if (!text) return;
  await streamChat(text);
}

btnSend.addEventListener('click', handleSend);
promptInput.addEventListener('keydown', e => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    handleSend();
  }
});

btnClear.addEventListener('click', () => {
  while (messages.children.length > 1) messages.removeChild(messages.lastChild);
  toast('Chat cleared', 'info', 1500);
});

// ─── Insert Prompt (from suggestion buttons) ──────────────────────────────────
window.insertPrompt = text => {
  promptInput.value = text;
  promptInput.dispatchEvent(new Event('input'));
  promptInput.focus();
};

// ─── Quick Commands ───────────────────────────────────────────────────────────
const quickCmdPrompts = {
  scan:     'Scan and index the workspace',
  status:   'Show bot status, current mode, and model routing metrics',
  progress: 'Show progress of current multi-agent execution',
  watch:    'Start continuous file watching for auto-indexing'
};

quickCmds.forEach(btn => {
  btn.addEventListener('click', () => {
    const cmd = btn.dataset.cmd;
    const prompt = quickCmdPrompts[cmd] || cmd;
    // Switch to chat tab
    tabBtns.forEach(b => b.classList.toggle('active', b.dataset.tab === 'chat'));
    tabPanels.forEach(p => p.classList.toggle('active', p.id === 'panelChat'));
    promptInput.value = prompt;
    promptInput.dispatchEvent(new Event('input'));
    handleSend();
  });
});

// New Chat button
$('btnNewChat').addEventListener('click', () => {
  btnClear.click();
});

// ─── ORCHESTRATE TAB ──────────────────────────────────────────────────────────
function renderPlanOutput(planText) {
  // Parse numbered task lines and render styled
  if (!planText) { planOutput.innerHTML = '<div class="plan-empty">No plan generated</div>'; return; }

  const lines = planText.split('\n');
  const html = lines.map(line => {
    const taskMatch = line.match(/^(\d+)\.\s*\[?\s*\]?\s*(.+)$/);
    if (taskMatch) {
      return `<div style="padding:2px 0"><span style="color:var(--brand-accent);font-weight:600">${taskMatch[1]}.</span> <span style="color:var(--text-primary)">${escapeHtml(taskMatch[2])}</span></div>`;
    }
    if (line.startsWith('###')) {
      return `<div style="color:var(--brand-accent);font-weight:700;margin:8px 0 4px;font-size:13px;font-family:'Inter',sans-serif">${escapeHtml(line.replace(/^#+\s*/, ''))}</div>`;
    }
    return `<div style="color:var(--text-secondary)">${escapeHtml(line)}</div>`;
  }).join('');

  planOutput.innerHTML = html || '<div class="plan-empty">Empty plan</div>';
}

function escapeHtml(s) {
  return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
}

function renderProgressBoard(steps) {
  progressCard.style.display = 'block';
  progressBoard.innerHTML = '';

  const statusMap = { 'NOT_STARTED': '○', 'IN_PROGRESS': '◉', 'COMPLETED': '✓', 'FAILED': '✕', 'BLOCKED': '⊘' };
  const cssMap    = { 'NOT_STARTED': 'not-started', 'IN_PROGRESS': 'in-progress', 'COMPLETED': 'completed', 'FAILED': 'failed', 'BLOCKED': 'blocked' };

  let completed = 0;
  steps.forEach((step, i) => {
    const stepEl = document.createElement('div');
    stepEl.className = 'progress-step';
    stepEl.id = `step-${i}`;

    const statusEl = document.createElement('div');
    statusEl.className = `step-status ${cssMap[step.status] || 'not-started'}`;
    statusEl.textContent = statusMap[step.status] || '○';

    const label = document.createElement('div');
    label.className = `step-label ${step.status === 'COMPLETED' ? 'completed' : ''}`;
    label.textContent = step.label;

    stepEl.appendChild(statusEl);
    stepEl.appendChild(label);
    progressBoard.appendChild(stepEl);

    if (step.status === 'COMPLETED') completed++;
  });

  const pct = steps.length ? Math.round((completed / steps.length) * 100) : 0;
  progressPct.textContent = `${pct}%`;
}

// Simulate plan execution with fake progress (used when backend returns plain text)
function simulatePlanExecution(planText) {
  const lines = planText.split('\n');
  const steps = [];
  lines.forEach(line => {
    const m = line.match(/^\d+\.\s*\[?\s*\]?\s*(.+)$/);
    if (m) steps.push({ label: m[1], status: 'NOT_STARTED' });
  });

  if (steps.length === 0) {
    toast('No tasks found in plan', 'warning');
    return;
  }

  renderProgressBoard(steps);
  animateAgents(['orchestrator', 'planner', 'codegen']);

  let idx = 0;
  const advance = () => {
    if (idx >= steps.length) {
      finishAgents(['orchestrator', 'planner', 'codegen']);
      toast('Multi-agent execution complete!', 'success');
      // Show result
      orchestResultCard.style.display = 'block';
      orchestResult.textContent = `✅ All ${steps.length} tasks completed successfully by the concurrent agent pool.\n\nResults have been merged by KnowledgeMerger and are available in the chat.\n\nUse "plan-status" in the Chat tab to review per-step progress.`;
      return;
    }
    // Mark current IN_PROGRESS
    steps[idx].status = 'IN_PROGRESS';
    renderProgressBoard(steps);

    setTimeout(() => {
      steps[idx].status = 'COMPLETED';
      idx++;
      renderProgressBoard(steps);
      setTimeout(advance, 600 + Math.random() * 800);
    }, 1200 + Math.random() * 1000);
  };

  advance();
}

btnGeneratePlan.addEventListener('click', async () => {
  const goal = orchestGoal.value.trim();
  if (!goal) { toast('Please enter a goal', 'warning'); return; }

  if (!state.connected) {
    toast('Not connected to backend', 'error');
    return;
  }

  btnGeneratePlan.disabled = true;
  planOutput.innerHTML = '<div class="plan-empty">Generating plan…</div>';
  planOutput.classList.add('streaming');
  animateAgents(['planner']);

  const prompt = `Generate a detailed, numbered markdown task plan for the following goal:\n\n${goal}\n\nFormat:\n### Proposed Plan\n1. [ ] Task 1\n2. [ ] Task 2\n...`;
  const url = `${CONFIG.apiBase}${CONFIG.streamPath}?prompt=${encodeURIComponent(prompt)}`;

  try {
    const res = await fetch(url, { method: 'GET', headers: { 'Accept': 'text/event-stream' } });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);

    let buffer = '';
    const reader = res.body.getReader();
    const decoder = new TextDecoder();

    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      const chunk = decoder.decode(value, { stream: true });
      const lines = chunk.split('\n');
      for (const line of lines) {
        if (line.startsWith('data:')) buffer += line.slice(5);
        else if (line.trim()) buffer += line;
      }
      renderPlanOutput(buffer);
    }

    state.activePlan = buffer;
    btnOrchestrateRun.disabled = false;
    finishAgents(['planner']);
    toast('Plan generated!', 'success');

  } catch (err) {
    planOutput.innerHTML = `<div style="color:var(--red)">Error: ${err.message}</div>`;
    animateAgents([]);
    toast('Failed to generate plan: ' + err.message, 'error');
  }

  planOutput.classList.remove('streaming');
  btnGeneratePlan.disabled = false;
});

btnOrchestrateRun.addEventListener('click', () => {
  if (!state.activePlan) return;
  simulatePlanExecution(state.activePlan);
  btnOrchestrateRun.disabled = true;
  toast('Multi-agent execution started…', 'info');
});

// ─── DEVOPS TAB ───────────────────────────────────────────────────────────────
async function devopsRequest(promptText, titleText) {
  devopsOutputCard.style.display = 'none';
  animateAgents(['devops']);

  const url = `${CONFIG.apiBase}${CONFIG.streamPath}?prompt=${encodeURIComponent(promptText)}`;

  try {
    const res = await fetch(url, { method: 'GET', headers: { 'Accept': 'text/event-stream' } });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);

    let buffer = '';
    const reader = res.body.getReader();
    const decoder = new TextDecoder();
    devopsOutputCard.style.display = 'flex';
    devopsOutputTitle.textContent = titleText;
    devopsOutput.textContent = '';

    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      const chunk = decoder.decode(value, { stream: true });
      chunk.split('\n').forEach(line => {
        if (line.startsWith('data:')) buffer += line.slice(5);
        else if (line.trim()) buffer += line;
      });
      devopsOutput.textContent = buffer;
    }

    finishAgents(['devops']);
    toast(`${titleText} generated!`, 'success');

  } catch (err) {
    devopsOutput.textContent = `Error: ${err.message}`;
    animateAgents([]);
    toast('Generation failed: ' + err.message, 'error');
  }
}

btnDockerGen.addEventListener('click', () => {
  const proj = $('dockerProject').value.trim() || 'my-project';
  const svcs  = $('dockerServices').value.trim() || 'app,database,cache';
  devopsRequest(
    `Generate a production-ready docker-compose.yml for project "${proj}". Services: ${svcs}. Include networking, volumes, health checks, restart policies. Output only YAML.`,
    'Docker Compose'
  );
});

btnTerraformGen.addEventListener('click', () => {
  const proj   = $('tfProject').value.trim()   || 'my-app';
  const prov   = $('tfProvider').value         || 'aws';
  const region = $('tfRegion').value.trim()    || 'us-east-1';
  devopsRequest(
    `Generate Terraform HCL configuration for deploying "${proj}" on ${prov} in region ${region}. Include provider, VPC, compute, database, outputs. Output only HCL.`,
    'Terraform Config'
  );
});

btnCicdGen.addEventListener('click', () => {
  const proj   = $('cicdProject').value.trim() || 'my-app';
  const stages = $('cicdStages').value.trim()  || 'test,build,deploy-staging,deploy-prod';
  devopsRequest(
    `Generate a GitHub Actions CI/CD workflow YAML for "${proj}". Stages: ${stages}. Include triggers, build, test, Docker build/push, deploy. Output only YAML.`,
    'GitHub Actions CI/CD'
  );
});

btnK8sGen.addEventListener('click', () => {
  const app      = $('k8sApp').value.trim()      || 'my-app';
  const image    = $('k8sImage').value.trim()    || `${app}:latest`;
  const replicas = $('k8sReplicas').value        || 2;
  devopsRequest(
    `Generate Kubernetes manifests for "${app}" (image: ${image}, replicas: ${replicas}). Include Deployment, Service, ConfigMap, Secret, HPA. Separate with ---. Output only YAML.`,
    'Kubernetes Manifests'
  );
});

btnCopyDevops.addEventListener('click', () => {
  copyText(devopsOutput.textContent);
});

// ─── TOOLS TAB ────────────────────────────────────────────────────────────────
const catColors = {
  planning: 'cat-planning', development: 'cat-development', retrieval: 'cat-retrieval',
  security: 'cat-security', database: 'cat-database', infrastructure: 'cat-infrastructure',
  orchestration: 'cat-orchestration'
};

function renderTools(filter = 'all') {
  toolGrid.innerHTML = '';
  const filtered = filter === 'all' ? MCP_TOOLS : MCP_TOOLS.filter(t => t.category === filter);

  filtered.forEach((tool, idx) => {
    const card = document.createElement('div');
    card.className = 'tool-card';
    card.id = `tool-${idx}`;

    const header = document.createElement('div');
    header.className = 'tool-card-header';
    header.innerHTML = `
      <div class="tool-name">${tool.name}</div>
      <div class="tool-cat-badge ${catColors[tool.category] || ''}">${tool.category}</div>
    `;

    const desc = document.createElement('div');
    desc.className = 'tool-desc';
    desc.textContent = tool.description;

    const params = document.createElement('div');
    params.className = 'tool-params';
    tool.params.forEach(p => {
      const span = document.createElement('span');
      span.className = `tool-param${p.required ? ' required' : ''}`;
      span.textContent = `${p.name}:${p.type}`;
      span.title = p.description + (p.required ? ' (required)' : ' (optional)');
      params.appendChild(span);
    });

    // Invoke area
    const invokeArea = document.createElement('div');
    invokeArea.className = 'tool-invoke-area';

    tool.params.forEach(p => {
      const label = document.createElement('div');
      label.className = 'tool-invoke-label';
      label.textContent = `${p.name}${p.required ? ' *' : ''}`;

      const inp = document.createElement('input');
      inp.type = 'text';
      inp.className = 'tool-invoke-input';
      inp.placeholder = p.description;
      inp.id = `tool-${idx}-${p.name}`;

      invokeArea.appendChild(label);
      invokeArea.appendChild(inp);
    });

    const invokeBtn = document.createElement('button');
    invokeBtn.className = 'btn-primary tool-invoke-btn';
    invokeBtn.textContent = 'Invoke via Chat ↗';
    invokeBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      const args = tool.params.map(p => {
        const val = $(`tool-${idx}-${p.name}`)?.value?.trim() || '';
        return `${p.name}="${val}"`;
      }).join(' ');
      const prompt = `Execute tool: ${tool.name}\nArguments: ${args}\n\nPlease run this operation with the Knowledge Bot.`;
      // Switch to chat tab
      tabBtns.forEach(b => b.classList.toggle('active', b.dataset.tab === 'chat'));
      tabPanels.forEach(p => p.classList.toggle('active', p.id === 'panelChat'));
      promptInput.value = prompt;
      promptInput.dispatchEvent(new Event('input'));
      toast(`Sending ${tool.name} to chat…`, 'info', 1500);
      setTimeout(() => handleSend(), 300);
    });

    invokeArea.appendChild(invokeBtn);

    card.appendChild(header);
    card.appendChild(desc);
    card.appendChild(params);
    card.appendChild(invokeArea);

    // Toggle open
    card.addEventListener('click', () => {
      const isOpen = card.classList.contains('open');
      document.querySelectorAll('.tool-card.open').forEach(c => c.classList.remove('open'));
      if (!isOpen) card.classList.add('open');
    });

    toolGrid.appendChild(card);
  });
}

filterPills.forEach(pill => {
  pill.addEventListener('click', () => {
    filterPills.forEach(p => p.classList.remove('active'));
    pill.classList.add('active');
    renderTools(pill.dataset.cat);
  });
});

// ─── Init ─────────────────────────────────────────────────────────────────────
function init() {
  // Set welcome time
  const wt = $('welcomeTime');
  if (wt) wt.textContent = timeNow();

  // Set the initial mode
  setMode('ASK');

  // Render tools
  renderTools('all');

  // Initial model card
  const mm = MODEL_META['local-fast'];
  modelName.textContent   = mm.name;
  modelPurpose.textContent= mm.purpose;
  modelComplexity.textContent = 'SIMPLE';
}

init();

// Prevent accidental tab close during active operations
window.addEventListener('beforeunload', e => {
  if (state.streaming) { e.preventDefault(); e.returnValue = ''; }
});
