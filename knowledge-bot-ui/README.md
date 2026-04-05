# Knowledge Bot UI — Setup Guide

## 🚀 How to Run Locally

The UI is a **zero-dependency** single-page application (pure HTML + CSS + JS). No npm, no Node.js required.

### Option 1 — Python HTTP Server (Recommended, avoids CORS)

```powershell
# Navigate to the UI folder
cd d:\AI-assistant-knowledgeBot\knowledge-bot-ui

# Start a local server on port 3000
python -m http.server 3000
```
Then open **http://localhost:3000** in your browser.

### Option 2 — VS Code Live Server Extension
1. Install the **Live Server** extension in VS Code
2. Right-click `index.html` → **Open with Live Server**

### Option 3 — Direct File Open (Quick Look Only)
Double-click `index.html` — the UI renders perfectly but chat calls will fail
due to browser CORS restrictions on `file://` URLs.

---

## 🔗 Connecting to the Backend

Make sure the **Spring Boot backend** is running first:

```powershell
cd d:\AI-assistant-knowledgeBot
.\mvnw spring-boot:run -pl knowledge-bot-web
```

The UI auto-connects to **`http://localhost:8080`** and shows the connection
indicator in the bottom-left of the sidebar.

> If you use a different port, edit `CONFIG.apiBase` in `app.js` (line ~17).

---

## 🧱 Infrastructure Requirements

| Component | Command |
|---|---|
| PostgreSQL (via Docker) | `docker-compose up -d` |
| Ollama (local AI) | `ollama serve` |
| Pull fast model | `ollama pull llama3.2:3b` |
| Pull code model | `ollama pull deepseek-coder-v2:16b` |
| Spring Boot API | `.\mvnw spring-boot:run -pl knowledge-bot-web` |

---

## 🎛️ UI Features at a Glance

### Sidebar
| Section | What it does |
|---|---|
| **MODE** | Switch between ASK / PLAN / CODE — mirrors `ModeManager.java` |
| **ACTIVE MODEL** | Shows which Ollama model is currently routing requests |
| **MULTI-AGENT** | Live status badges for all 5 agents (Orchestrator, Planner, CodeGen, Reflection, DevOps) |
| **QUICK COMMANDS** | One-click shortcuts for Scan, Status, Progress, Watch |
| **Connection** | Auto-pings backend every 8 s |

### Tabs
| Tab | Purpose |
|---|---|
| **Chat** | Main conversational interface — streams responses from `GET /api/v1/chat/simple` |
| **Orchestrate** | Send a goal → generate a plan → watch it execute with DAG agents |
| **DevOps** | Generate Docker, Terraform, GitHub Actions, Kubernetes configs |
| **Tools** | Browse and invoke all 12 MCP tools from `McpToolRegistry` |

### Mode Behavior (matches backend exactly)
| Mode | File Access | Model | Complexity |
|---|---|---|---|
| **ASK** | Read-only | llama3.2:3b | SIMPLE |
| **PLAN** | Docs only | deepseek-coder-v2 | COMPLEX |
| **CODE** | Full | deepseek-coder-v2 | MODERATE |
