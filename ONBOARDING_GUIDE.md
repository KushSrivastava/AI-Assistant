# Knowledge Bot — Junior Developer Onboarding Guide

> **Welcome!** This document is your complete handbook for understanding, maintaining, and extending the Knowledge Bot. It assumes you know basic Java but have never seen this codebase before. Read every section in order on your first day — it will save you days of confusion.

---

## Table of Contents

1. [Project Overview — The "What"](#1-project-overview--the-what)
2. [High-Level Architecture & System Flow — The "Where"](#2-high-level-architecture--system-flow--the-where)
3. [Module-by-Module Breakdown](#3-module-by-module-breakdown)
4. [Core Class Deep-Dive — The "How" & "Why"](#4-core-class-deep-dive--the-how--why)
5. [How the Application Talks to AI Models](#5-how-the-application-talks-to-ai-models)
6. [The Three Operating Modes](#6-the-three-operating-modes)
7. [The RAG Pipeline — From File to Answer](#7-the-rag-pipeline--from-file-to-answer)
8. [Security Layer](#8-security-layer)
9. [Infrastructure — Docker & Database](#9-infrastructure--docker--database)
10. [How-To Extension Guide](#10-how-to-extension-guide)
11. [Best Practices](#11-best-practices)
12. [Troubleshooting & Common Errors](#12-troubleshooting--common-errors)

---

## 1. Project Overview — The "What"

The **Knowledge Bot** is a 100% offline, enterprise-grade AI assistant built on:

| Technology | Version | Role |
|---|---|---|
| Java | 21 (LTS) | Application language |
| Spring Boot | 3.4.4 | Application framework |
| Spring AI | 1.1.0 | AI abstraction layer |
| Ollama | latest | Local LLM runtime |
| PostgreSQL + pgvector | pg17 | Vector store for RAG |
| Spring Shell | included | Interactive CLI |

**What does it do?**

You point it at a codebase folder. It reads every file, converts the content into mathematical "vector" representations (called *embeddings*), and stores them in a database. When you ask it a question, it finds the most relevant chunks of code/docs using vector similarity search, then feeds those chunks + your question to a local AI model and streams back the answer. This is called **Retrieval-Augmented Generation (RAG)**.

**Why is this valuable?** The AI never needs to see your entire codebase at once. Instead, it only sees the top 5 most relevant snippets — keeping cost (tokens) low and accuracy high.

---

## 2. High-Level Architecture & System Flow — The "Where"

```
┌─────────────────────────────────────────────────────────────────────┐
│                         USER INTERFACES                              │
│                                                                      │
│  ┌──────────────────────┐        ┌──────────────────────────────┐   │
│  │  Browser UI           │        │  Spring Shell CLI             │   │
│  │  knowledge-bot-ui/    │        │  knowledge-bot-cli/           │   │
│  │  index.html + app.js  │        │  KnowledgeBotCommands.java    │   │
│  └──────────┬───────────┘        └──────────────┬───────────────┘   │
│             │ HTTP/SSE                           │ Direct Service     │
└─────────────┼─────────────────────────────────-- ┼────────────────────┘
              │                                    │
              ▼                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│                       knowledge-bot-web                              │
│                                                                      │
│   ChatController  (/api/v1/chat)                                     │
│   CacheConfig     (Caffeine in-memory cache)                         │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│                       knowledge-bot-ai                               │
│                                                                      │
│   ┌─────────────────────┐   ┌─────────────────────┐                 │
│   │  ModelRouterService  │──▶│  ChatClientFactory   │                 │
│   │  (smart routing)     │   │  (builds ChatClients)│                 │
│   └──────┬──────────────┘   └─────────────────────┘                 │
│          │                                                            │
│   ┌──────▼──────────────────────────────────────────┐               │
│   │  IntentClassifier → TaskComplexity Assessment   │               │
│   └──────┬──────────────────────────────────────────┘               │
│          │                                                            │
│   ┌──────▼──────────┐   ┌──────────────────┐   ┌─────────────────┐ │
│   │ PlanningService │   │ OrchestratorSvc  │   │ EmbeddingPipeline│ │
│   │ (plan-first AI) │   │ (DAG execution)  │   │ (file → vectors) │ │
│   └─────────────────┘   └──────────────────┘   └─────────────────┘ │
│                                                                      │
│   ┌──────────────────────────────────────────────────────────────┐  │
│   │  RetrievalService (vector search + fuzzy fallback)           │  │
│   └──────────────────────────────────────────────────────────────┘  │
│                                                                      │
│   ┌──────────────────────────────────────────────────────────────┐  │
│   │  PromptCompositionService (builds the final AI prompt)       │  │
│   └──────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────┘
              │
              ▼
┌─────────────────────────────────────────────────────────────────────┐
│                       knowledge-bot-core                             │
│                                                                      │
│   WorkspaceScannerService   SemanticIndexingService                  │
│   TokenBudgetService        PromptInjectionGuard                    │
│   OutputSanitizer           CommandPermissionService                 │
│   AgentTraceInterceptor     HybridSearchService                     │
└──────────────────────────────────────────────────────────────────────┘
              │
              ▼
┌─────────────────────────────────────────────────────────────────────┐
│                       knowledge-bot-data                             │
│                                                                      │
│   VectorStoreConfig (PostgreSQL pgvector bean)                       │
│   GlobalKnowledgeService  WorklogService                             │
└──────────────────────────────────────────────────────────────────────┘
              │
              ▼
┌─────────────────────────────────────────────────────────────────────┐
│                  EXTERNAL INFRASTRUCTURE (Docker)                    │
│                                                                      │
│   Ollama :11434  (local LLM — llama3.2:3b default)                  │
│   PostgreSQL :5433  (pgvector extension for vector similarity)       │
└─────────────────────────────────────────────────────────────────────┘
```

### Complete Request Flow (Chat)

When a user types `chat what does the OrderService do?` in the CLI:

```
1. KnowledgeBotCommands.chat(query)
       │
       ├─► PromptInjectionGuard.isSafe(query)          ← security check
       ├─► TokenBudgetService.assertBudgetAvailable()   ← cost guard
       ├─► RetrievalService.search(query, 5)            ← find top-5 relevant docs
       ├─► PromptCompositionService.buildPrompt(...)    ← compose system+user prompt
       ├─► ModelRouterService.getClientForPrompt(query) ← pick best AI model
       │       └─► IntentClassifier.classify(query)
       │       └─► ModelRegistry.selectAdaptive(complexity)
       │       └─► ChatClientFactory.getClientForModel(descriptor)
       │
       ├─► chatClient.prompt(prompt).call().content()   ← call Ollama
       │       └─► MetricsAdvisor records latency/errors
       │
       └─► OutputSanitizer.sanitize(response)           ← strip dangerous patterns
               └─► return to user
```

---

## 3. Module-by-Module Breakdown

The project is a **Maven multi-module** build. Each module is a separate JAR with a specific responsibility.

```
knowledge-bot-parent/          ← Root POM, defines all versions centrally
├── knowledge-bot-common/      ← Shared DTOs, utilities (currently minimal)
├── knowledge-bot-core/        ← File scanning, security, observability
├── knowledge-bot-ai/          ← All AI logic: routing, RAG, planning, orchestration
├── knowledge-bot-data/        ← Database & vector store configuration
├── knowledge-bot-web/         ← REST API (Spring Boot entry point for web)
├── knowledge-bot-cli/         ← Spring Shell CLI (second entry point)
├── knowledge-bot-mcp/         ← Model Context Protocol server/client
└── knowledge-bot-ui/          ← Standalone HTML/CSS/JS frontend
```

### Dependency Graph

```
knowledge-bot-web
    └── knowledge-bot-data
            └── knowledge-bot-ai
                    └── knowledge-bot-core
                            └── knowledge-bot-common
```

> **Key Rule**: Lower modules cannot import from higher modules. For example, `knowledge-bot-core` must never import from `knowledge-bot-ai`. This enforces clean separation of concerns.

---

## 4. Core Class Deep-Dive — The "How" & "Why"

### 4.1 `ModelDescriptor` (Record)
**Package:** `com.knowledgebot.ai.model`

```java
public record ModelDescriptor(
    ModelProvider provider,      // OLLAMA or OPENAI
    String modelName,            // e.g. "llama3.2:3b"
    TaskComplexity minComplexity,
    TaskComplexity maxComplexity,
    double costPer1kTokens,
    double maxTokens,
    int maxContextTokens,
    int recommendedPromptBudget,
    String purpose
) { ... }
```

**Why a Java Record?** Records are immutable value objects - perfect for model configuration that should never change at runtime. You cannot accidentally mutate a model's parameters mid-request.

The `canHandle(TaskComplexity)` method is the core routing filter:
```java
public boolean canHandle(TaskComplexity complexity) {
    return complexity.compareTo(minComplexity) >= 0
        && complexity.compareTo(maxComplexity) <= 0;
}
```
This means each model has a "complexity band" it handles. The `SIMPLE` model only handles `SIMPLE` tasks; the `advanced-reasoning` model handles `COMPLEX` through `REASONING_HEAVY`.

---

### 4.2 `TaskComplexity` (Enum)

```java
public enum TaskComplexity {
    SIMPLE,          // "what is", "explain", "list"
    MODERATE,        // "generate", "refactor", "analyze"
    COMPLEX,         // "architecture", "pipeline", "deploy"
    REASONING_HEAVY  // "debug", "root cause", "why does"
}
```

The enum values have a natural ordering (SIMPLE < MODERATE < COMPLEX < REASONING_HEAVY) — this is what makes `compareTo()` work in `canHandle()`.

---

### 4.3 `IntentClassifier`
**Package:** `com.knowledgebot.ai.model`

This service reads a user prompt and returns a `TaskComplexity`. It uses **keyword matching** (not a second AI call — that would be wasteful):

```java
// Priority order matters! REASONING_HEAVY is checked first.
if (REASONING_KEYWORDS.stream().anyMatch(lower::contains)) return REASONING_HEAVY;
if (COMPLEX_KEYWORDS.stream().anyMatch(lower::contains))   return COMPLEX;
if (MODERATE_KEYWORDS... || prompt.length() > 200)         return MODERATE;
if (SIMPLE_KEYWORDS...   || prompt.length() <= 100)        return SIMPLE;
return MODERATE; // safe default
```

**Why keyword-first, not AI-first?** Calling an AI model to classify a query introduces latency and cost before you even answer the user's actual question. Simple heuristics are fast, predictable, and free.

**How to extend:** If you add a new keyword category, add it to the appropriate `Set<String>` constant. If you need a new complexity level, add it to the enum AND update the `canHandle` logic in `ModelDescriptor`.

---

### 4.4 `ModelMetrics`
**Package:** `com.knowledgebot.ai.model`

Tracks live runtime performance of each model. The key concept here is the **Exponential Moving Average (EMA)**:

```java
private static final double ALPHA = 0.2;

public void recordSuccess(long latencyMs) {
    // EMA formula: new_avg = α × latest_value + (1−α) × old_avg
    avgLatencyMs.updateAndGet(old -> ALPHA * latencyMs + (1 - ALPHA) * old);
}
```

**Why EMA instead of a simple average?** A simple average treats a response from 10 minutes ago equally to one from 1 second ago. EMA gives more weight to recent observations. `ALPHA = 0.2` means roughly the last 5 observations dominate.

**Error penalty:** When a call fails, the apparent latency is artificially boosted: `old * 5`. This means a model that keeps failing will look very slow to the router and will be deprioritized automatically.

**Score formula** (lower is better):
```
score = 0.5 × (latency / maxLatencyInPool)
      + 0.5 × (cost / maxCostInPool)
      + (errorRate × 2.0)
```

---

### 4.5 `ModelRegistry`
**Package:** `com.knowledgebot.ai.model`

This `@Configuration` class reads all model parameters from `application.yml` via `@Value` annotations and assembles five `ModelDescriptor` instances at startup.

**Why so many `@Value` fields?** Because every single parameter of every model is externally configurable without recompiling. To swap `llama3.2:3b` for `llama3.3:7b` in the `local-fast` slot, you edit one line in `application.yml`. No Java code changes needed.

The five registered model slots:
| Key | Default Model | Handles |
|---|---|---|
| `local-fast` | llama3.2:3b | SIMPLE only |
| `local-code` | llama3.2:3b | SIMPLE–MODERATE |
| `local-fallback` | llama3.2:3b | Any |
| `advanced-reasoning` | llama3.2:3b | COMPLEX–REASONING_HEAVY |
| `advanced-creative` | llama3.2:3b | MODERATE–REASONING_HEAVY |

> **Note:** Currently all 5 slots use `llama3.2:3b` as configured in `application.yml`. This is because the dev machine runs with limited RAM. To use the intended models like `deepseek-coder-v2:16b`, update the yml and pull them via Ollama.

---

### 4.6 `ModelRouterService`
**Package:** `com.knowledgebot.ai.model`

The brain of the routing system. Every public ChatClient request goes through here.

```java
public ChatClient getClientForPrompt(String prompt) {
    ModelDescriptor descriptor = selectModel(prompt);
    return chatClientFactory.getClientForModel(descriptor, modelRegistry);
}
```

Internally, `selectAdaptive(complexity)` does:
1. Filter all models to those whose `canHandle(complexity)` returns true
2. If only one candidate: return it directly
3. If multiple: compute the weighted score for each and return the lowest-score model

**The `@PostConstruct init()` method** runs once at startup:
- Seeds a `ModelMetrics` map entry for every registered model
- Injects the metrics map into `ChatClientFactory` (so `MetricsAdvisor` can write to it)
- Schedules a background health-snapshot log every 60 seconds using a daemon thread

---

### 4.7 `ChatClientFactory`
**Package:** `com.knowledgebot.ai.model`

Builds and caches Spring AI `ChatClient` objects. A `ChatClient` is the main object you use to send prompts to an AI model.

```java
private ChatClient buildClientForDescriptor(ModelDescriptor descriptor) {
    MetricsAdvisor advisor = buildAdvisor(descriptor.modelName());
    return ChatClient.builder(ollamaChatModel)
            .defaultOptions(OllamaChatOptions.builder()
                    .model(descriptor.modelName())  // tells Ollama WHICH model to use
                    .temperature(0.2)               // low = more deterministic
                    .build())
            .defaultAdvisors(advisor)               // wraps every call with metrics
            .build();
}
```

**Why cache?** Creating a `ChatClient` is not free — it involves object allocation and configuration. Since the same model will be called many times, the factory caches the built client in a `ConcurrentHashMap` (thread-safe).

**Why `temperature(0.2)`?** Lower temperature means the model picks more predictable, "safe" word choices. For a developer assistant answering technical questions, you want accuracy, not creativity. Set to `1.0` for creative tasks.

---

### 4.8 `MetricsAdvisor`
**Package:** `com.knowledgebot.ai.model`

This implements Spring AI's `CallAdvisor` interface — a **decorator/interceptor** pattern that wraps every AI call transparently.

```java
public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
    long start = System.currentTimeMillis();
    try {
        ChatClientResponse response = chain.nextCall(request); // actual AI call
        metrics.recordSuccess(System.currentTimeMillis() - start);
        return response;
    } catch (Exception e) {
        metrics.recordError();
        throw e; // re-throw so the caller still sees the error
    }
}
```

**Why `getOrder() = LOWEST_PRECEDENCE`?** Advisors form a chain (like Spring MVC filters). Running last means we measure the total round-trip time including any other advisors in the chain, giving us the most accurate latency figure.

---

### 4.9 `EmbeddingPipeline`
**Package:** `com.knowledgebot.ai.pipeline`

This is the **indexing** side of RAG. When you run `scan <path>`, this processes every file.

```java
public void processAndEmbed(List<Path> files) {
    for (Path file : files) {
        Document doc = buildDocument(file);       // extract content
        List<Document> chunks = chunkingService.chunk(doc); // split into pieces
        vectorStore.add(chunks);                  // store in PostgreSQL pgvector
    }
}
```

**The Smart Java Handling:**
```java
// Java files → SemanticIndexingService (JavaParser-based)
if (semanticIndexingService.supports(file)) {
    String semanticContent = semanticIndexingService.buildSemanticSummary(file);
    // ... also extracts call graph and import graph
}
// Other files → Apache Tika parser (PDF, DOCX, etc.)
// Raw fallback → Files.readString()
```

**Why different handling for Java files?** Raw Java source code wastes precious context tokens on method bodies, comments, and boilerplate. The `SemanticIndexingService` uses **JavaParser** to extract only the structural information (class declarations, field types, method signatures, imports) — like a table of contents. This improves embedding quality and reduces token consumption.

---

### 4.10 `SemanticIndexingService`
**Package:** `com.knowledgebot.core.scanner`

Uses the **JavaParser library** (not an AI model) to parse Java source files into an AST (Abstract Syntax Tree), then formats a compact structural summary.

Output format for a Java file:
```
package com.example;

// Dependencies: com.knowledgebot, org.springframework

public class OrderService {

  private OrderRepository repository;

  public Order createOrder(String customerId, List<Item> items);
  // calls: save, findById, validate

  public void cancelOrder(String orderId);
  // calls: findById, delete, notify

}
```

This is the content that gets vectorized and stored. Notice: **no method bodies**. This dramatically reduces token count while preserving the information the AI needs to answer "what does OrderService do?"

---

### 4.11 `RetrievalService`
**Package:** `com.knowledgebot.ai.retrieval`

This is the **search** side of RAG. Has a 4-layer fallback strategy:

```
1. @Mention Check    → if query contains "@filename", load that file directly
         ↓ (if no mention)
2. Vector Search     → semantic similarity against pgvector database (threshold: 0.7)
         ↓ (if results found)
3. Auto-Prune        → if any result > 2000 tokens, extract only method signatures
         ↓ (if vector search returns empty)
4. Fuzzy Grep        → keyword search across raw files on disk
```

**Why 0.7 similarity threshold?** Below 0.7, results become too loosely related and add noise to the prompt. You can tune this in the code if you're getting too few or too many results.

**Why auto-prune large docs?** Research shows that LLMs suffer from "Lost in the Middle" — they pay attention to the beginning and end of context but ignore the middle. By extracting only signatures from large files, we keep context compact and focused.

---

### 4.12 `PromptCompositionService`
**Package:** `com.knowledgebot.ai.prompt`

Assembles the final prompt sent to the AI:

```java
private static final String DEFAULT_QA_TEMPLATE =
    "You are an expert developer assistant. Use the following code context to answer the user's question.\n" +
    "If the answer is not in the context, use your best programming knowledge.\n\n" +
    "CONTEXT:\n{context}\n\n" +
    "QUESTION: {question}";
```

Each retrieved document is formatted as:
```
--- File: OrderService.java ---
[semantic summary content]

--- File: OrderRepository.java ---
[semantic summary content]
```

**Why include the filename?** The AI uses filename as a hint. "OrderService.java" tells it this is a service class. Without filenames, the AI has no structural context about where the code lives.

---

### 4.13 `PlanningService`
**Package:** `com.knowledgebot.ai.planning`

Implements the "Plan-First" agentic architecture. Instead of immediately acting, the bot generates a plan and presents it for review.

```java
// The system prompt given to the AI for plan generation:
"""
You are a Technical Orchestrator for the Knowledge Bot.
Take the user's intent and break it down into a numbered Markdown task list.
Do NOT execute anything yet. Just propose the PLAN.

Format:
### Proposed Plan
1. [ ] Task 1 Description
2. [ ] Task 2 Description
"""
```

**Why plan-first?** It enables "Human-in-the-Loop" review. The user can reject the plan before any file modifications happen. This is safety-critical for agentic systems.

The `parseSteps(markdown)` method uses regex to extract `1. [ ] Task description` lines from the markdown:
```java
private static final Pattern TASK_LINE =
    Pattern.compile("^\\d+\\.\\s*(?:\\[.*?]\\s*)?(.+)$", Pattern.MULTILINE);
```

---

### 4.14 `DAGScheduler`
**Package:** `com.knowledgebot.ai.orchestration`

The most sophisticated class in the system. Executes multiple AI tasks concurrently as a **Directed Acyclic Graph (DAG)**.

**Key concept — Virtual Threads (Java 21 Project Loom):**
```java
for (DagTask task : taskGraph) {
    Thread.startVirtualThread(() -> {
        waitForDependencies(task);  // blocks cheaply on virtual thread
        executeTask(task, chatClient);
    });
}
```

Virtual threads are extremely lightweight (thousands can run concurrently vs. dozens for platform threads). Each task waits for its dependencies by sleeping (`Thread.sleep(100)`) — on a virtual thread, this sleep costs almost no system resources.

**Stuck-State Detection:** If a task produces the same output multiple times, it's "stuck". The scheduler recovers by injecting a different prompt on retry:
```
"PREVIOUS ATTEMPTS PRODUCED IDENTICAL RESULTS — CHANGE YOUR STRATEGY.
Try a completely different approach or break the task into smaller sub-steps."
```

---

### 4.15 `WorkspaceManager`
**Package:** `com.knowledgebot.ai.model`

Maintains the "active workspace" — the directory that CODE and PLAN modes operate within.

```java
// AtomicReference ensures thread-safe reads/writes across concurrent requests
private final AtomicReference<Path> activeWorkspace = new AtomicReference<>();
```

**Why AtomicReference?** The CLI could theoretically be used from multiple threads. `AtomicReference` ensures that reading and writing the workspace path is atomic — you never see a half-updated state.

---

### 4.16 `TokenBudgetService`
**Package:** `com.knowledgebot.core.performance`

Implements a **circuit breaker** for token consumption. Once the session consumes more than the budget (default: 50,000 tokens), the circuit "opens" and all further calls throw `TokenBudgetExceededException`.

```java
// Token estimation: chars ÷ 3.5 ≈ tokens (standard approximation)
public long estimateTokens(String text) {
    return (long) (text.length() / 3.5);
}
```

This service also uses **JavaParser** to extract method signatures for pruning — an interesting dual use where the same library serves both semantic indexing (EmbeddingPipeline) and context pruning (TokenBudgetService).

---

### 4.17 `McpClientService` (MCP Module)
**Package:** `com.knowledgebot.mcp`

The **Model Context Protocol (MCP)** is an emerging standard for connecting AI models to external tools. This module implements both a client (to call external MCP servers) and `McpDomainServer` (to expose Knowledge Bot capabilities as tools to other AI systems).

The client uses JSON-RPC 2.0 over HTTP:
```json
{
    "jsonrpc": "2.0",
    "method": "tools/call",
    "params": { "name": "search", "arguments": { "query": "..." } }
}
```

This is an **advanced/future feature** — understand it exists but focus on the RAG pipeline and routing first.

---

## 5. How the Application Talks to AI Models

This is the most important section for understanding AI integration.

### Step 1: Spring AI Auto-Configuration

When the application starts, Spring AI reads `application.yml`:
```yaml
spring:
  ai:
    ollama:
      base-url: http://localhost:11434
      chat:
        model: llama3.2:3b
```

Spring AI automatically creates an `OllamaChatModel` bean. This bean handles all HTTP communication with Ollama.

### Step 2: ChatClient Construction

`ChatClientFactory` takes the auto-configured `OllamaChatModel` and wraps it:
```java
ChatClient.builder(ollamaChatModel)
    .defaultOptions(OllamaChatOptions.builder()
        .model("llama3.2:3b")     // override which model to call
        .temperature(0.2)
        .build())
    .defaultAdvisors(metricsAdvisor)
    .build()
```

Think of `ChatClient` as a configured HTTP client for AI calls — similar to `RestTemplate` but for AI.

### Step 3: Making a Call

**Streaming (Web API — Server-Sent Events):**
```java
// Returns a reactive Flux<String> — chunks arrive as the model generates them
return modelRouterService.getClientForPrompt(prompt)
    .prompt()
    .user(prompt)
    .stream()      // ← key! starts streaming
    .content();
```

**Blocking (CLI):**
```java
// Waits for the full response
String response = chatClient.prompt(prompt).call().content();
```

### Step 4: Under the Hood — What Ollama Receives

Spring AI sends an HTTP POST to `http://localhost:11434/api/chat`:
```json
{
  "model": "llama3.2:3b",
  "messages": [
    { "role": "system", "content": "You are an expert developer assistant..." },
    { "role": "user",   "content": "CONTEXT:\n...\n\nQUESTION: what does OrderService do?" }
  ],
  "options": { "temperature": 0.2 },
  "stream": true
}
```

Ollama responds with a stream of JSON chunks, which Spring AI reassembles into the `Flux<String>`.

### Step 5: Multimodal (Image-to-Code)

For vision tasks (UI screenshot → code):
```java
chatClient.prompt()
    .user(u -> u.text("Build an Angular component for this")
                .media(MimeTypeUtils.IMAGE_PNG, new ByteArrayResource(imageBytes)))
    .call()
    .content();
```

This only works with vision-capable models (like `llava:7b`). The default `llama3.2:3b` does not support images.

---

## 6. The Three Operating Modes

The bot operates in one of three modes controlled by `ModeManager`:

| Mode | File Access | Use Case |
|---|---|---|
| `ASK` (default) | Read-only | Q&A about code, explanations |
| `PLAN` | Create `.md` files only | Generate and save plans |
| `CODE` | Full create/modify/delete | Code generation, modernization |

**Switching modes requires an attached workspace for CODE and PLAN:**
```
> attach-workspace C:\projects\myapp
> set-mode CODE
```

**Why this enforcement?** Preventing accidental file modifications. You cannot accidentally run a code-generation command in ASK mode and corrupt production files.

**Mode check pattern used throughout the CLI:**
```java
@ShellMethod(key = "generate", value = "Generate code based on instructions")
public String generate(@ShellOption String prompt, ...) {
    if (!modeManager.canModifyFiles()) {
        return "[MODE BLOCK] Switch to CODE mode first. Use: set-mode CODE";
    }
    // ... proceed safely
}
```

---

## 7. The RAG Pipeline — From File to Answer

### Phase 1: Indexing (one-time setup, or triggered by `scan` command)

```
scan <path>
    │
    ▼
WorkspaceScannerService.scanWorkspace(path)
    │  Walks file tree, skips: .git, target, node_modules, .idea
    │  Includes: .java .xml .yaml .yml .properties .md .ts .js .html .css .sql .json .txt
    ▼
EmbeddingPipeline.processAndEmbed(files)
    │
    ├─ For .java files:
    │       SemanticIndexingService.buildSemanticSummary()  ← JavaParser AST extraction
    │
    ├─ For PDF/DOCX/etc:
    │       MultiFormatParser (Apache Tika)
    │
    └─ For everything else:
            Files.readString()
    │
    ▼
CodeChunkingService.chunk(document)
    │  Splits large documents into overlapping chunks (prevents losing context at boundaries)
    ▼
VectorStore.add(chunks)
    │  Ollama generates embeddings using nomic-embed-text model
    │  pgvector stores them in PostgreSQL table: global_knowledge_vectors
    └─ Done
```

### Phase 2: Query (happens on every `chat` command)

```
chat "what does OrderService do?"
    │
    ▼
RetrievalService.search(query, topK=5)
    │  1. Check for @filename mention
    │  2. VectorStore.similaritySearch (threshold 0.7)
    │  3. Auto-prune results > 2000 tokens
    │  4. Fallback to grep if empty
    ▼
PromptCompositionService.buildPrompt(question, docs)
    │  Assembles: system instruction + CONTEXT (retrieved docs) + QUESTION
    ▼
ModelRouterService.getClientForPrompt(query)
    │  Classify complexity → select best model → return ChatClient
    ▼
ChatClient.prompt(assembled_prompt).call().content()
    │  HTTP POST to Ollama
    ▼
OutputSanitizer.sanitize(response)
    │  Check for dangerous patterns before returning
    ▼
Return to user
```

---

## 8. Security Layer

The system has four distinct security components. Understand them all before writing any command handler.

### 8.1 `PromptInjectionGuard`
Blocks attempts to hijack the AI's behavior:
```java
private static final List<String> INJECTION_PATTERNS = List.of(
    "ignore all previous instructions",
    "forget your previous instructions",
    "system prompt",
    "you are no longer"
);
```
**To add a new pattern:** Simply add a string to this list.

### 8.2 `OutputSanitizer`
Warns when AI-generated code contains dangerous operations:
```java
private static final List<String> DANGEROUS_PATTERNS = List.of(
    "Runtime.getRuntime().exec",  // shell command execution
    "ProcessBuilder",
    "System.exit(",
    ".delete(",
    "Files.delete"
);
```
Note: This does **not** block the output — it prepends a ⚠️ warning banner. The developer still receives the code but is alerted to review carefully.

### 8.3 `CommandPermissionService` (Human-in-the-Loop)
When `safeMode = true` (the default), any destructive command pauses and asks the user:
```
[PERMISSION REQUIRED] User: Anonymous | The AI wants to: Analyze and modernize file: OrderService.java
Approve? (y/n):
```
Disable safe mode with: `safe-mode false` (for automated/CI environments).

### 8.4 `TokenBudgetService` (Circuit Breaker)
Prevents runaway token consumption in long agentic sessions. Default budget: 50,000 tokens per session. Reset with `resetSession()`.

---

## 9. Infrastructure — Docker & Database

### Starting the Infrastructure

```bash
docker-compose up -d
```

This starts two containers:

| Container | Image | Port | Purpose |
|---|---|---|---|
| `knowledge-bot-ollama` | `ollama/ollama:latest` | `11434` | Runs AI models locally |
| `knowledge-bot-postgres` | `pgvector/pgvector:pg17` | `5433` | Vector store + metadata |

**Database credentials** (from `docker-compose.yml`):
```
Host:     localhost
Port:     5433  ← Note: NOT 5432 (avoids conflicts with local Postgres)
Database: knowledgebot
User:     kbot
Password: password
```

### Pulling AI Models

After Ollama starts, pull the required models:
```bash
docker exec -it knowledge-bot-ollama ollama pull llama3.2:3b
docker exec -it knowledge-bot-ollama ollama pull nomic-embed-text  # for embeddings
```

### The pgvector Table

Spring AI automatically creates the vector table (`global_knowledge_vectors`) on first startup when `initialize-schema: true` is set in `application.yml`. The table stores:
- Document text chunks
- 768-dimensional embedding vectors (from `nomic-embed-text`)
- Metadata (filename, absolutePath, indexType, etc.)

---

## 10. How-To Extension Guide

### 10.1 How to Update to a New AI Model

**Scenario:** You want to upgrade from `llama3.2:3b` to `llama3.3:7b` for better quality.

**Step 1:** Pull the new model in Ollama:
```bash
docker exec -it knowledge-bot-ollama ollama pull llama3.3:7b
```

**Step 2:** Edit `application.yml`:
```yaml
knowledge-bot:
  model-routing:
    local-fast-model: llama3.3:7b         # was: llama3.2:3b
    local-fast-context: 32768             # update if new model has larger context
    local-fast-budget: 8000               # update recommended prompt budget
```

**Step 3:** Restart the application. No Java code changes needed.

**Step 4:** Monitor the routing metrics via `status` command.

> **If you need a completely new model slot** (e.g. adding a `gpt-4o` cloud slot):
> 1. Add `ModelProvider.OPENAI` to the `ModelProvider` enum
> 2. Add the new model's `@Value` fields to `ModelRegistry`
> 3. Add the new `ModelDescriptor` entry in `ModelRegistry.init()`
> 4. Add Spring AI OpenAI starter to `pom.xml`
> 5. Update `ChatClientFactory` to handle `ModelProvider.OPENAI` (build an OpenAI client instead of Ollama client)

---

### 10.2 How to Add a Completely New CLI Command

**Scenario:** Add a `summarize-file <path>` command that summarizes a file's purpose.

**Step 1:** Identify which service will do the heavy lifting. For a summarization, `ModelRouterService` and `PromptCompositionService` are all we need.

**Step 2:** Add the method to `KnowledgeBotCommands.java`:

```java
@ShellMethod(key = "summarize-file", value = "Summarize what a file does")
public String summarizeFile(@ShellOption String path) {
    // 1. Security checks first
    if (!injectionGuard.isSafe(path)) {
        return "[SECURITY BLOCK] Unsafe input detected.";
    }

    // 2. Budget check
    tokenBudgetService.assertBudgetAvailable();

    // 3. Resolve the path relative to the active workspace
    Path targetPath = workspaceManager.resolve(path);

    // 4. Read the file content
    String content;
    try {
        content = java.nio.file.Files.readString(targetPath);
    } catch (java.io.IOException e) {
        return "[ERROR] Cannot read file: " + e.getMessage();
    }

    // 5. Build and send the prompt
    ChatClient client = modelRouterService.getClientForComplexity(TaskComplexity.SIMPLE);
    String response = client.prompt()
            .system("You are an expert code reviewer. Summarize what the following file does in 3-5 sentences.")
            .user(content)
            .call()
            .content();

    // 6. Count tokens spent
    tokenBudgetService.addTokens(tokenBudgetService.estimateTokens(content + response));

    // 7. Sanitize and return
    return sanitizer.sanitize(response);
}
```

**Step 3:** No registration needed — Spring Shell automatically discovers all `@ShellMethod` methods in `@ShellComponent` classes.

**Step 4:** Test by running the CLI and typing `summarize-file src/main/java/MyClass.java`.

---

### 10.3 How to Add a New REST API Endpoint

**Scenario:** Add a `/api/v1/chat/summarize` endpoint that summarizes text via HTTP.

**Step 1:** Add the method to `ChatController.java`:

```java
@PostMapping("/summarize")
public ResponseEntity<Map<String, String>> summarize(
        @RequestBody Map<String, String> request) {

    String text = request.get("text");
    if (text == null || text.isBlank()) {
        return ResponseEntity.badRequest()
            .body(Map.of("error", "Missing 'text' field"));
    }

    // Use the router to pick the best model for a simple task
    ChatClient client = modelRouterService.getClientForComplexity(TaskComplexity.SIMPLE);
    String summary = client.prompt()
            .system("Summarize the following text in 2-3 sentences.")
            .user(text)
            .call()
            .content();

    return ResponseEntity.ok(Map.of("summary", summary));
}
```

**Step 2:** Test with curl:
```bash
curl -X POST http://localhost:8080/api/v1/chat/summarize \
  -H "Content-Type: application/json" \
  -d '{"text": "Your long text here..."}'
```

---

### 10.4 How to Safely Remove an Existing Feature

**Scenario:** You want to remove the `deploy-terraform` command temporarily.

**Step 1:** Before removing, search for all usages:
```bash
# In the project root
grep -r "deployTerraform\|deploy-terraform\|DeploymentAgent" --include="*.java" .
```

**Step 2:** Check if the method is called anywhere outside its own class:
- If a method is called from multiple places, removing it breaks the callers
- If it's only called from one `@ShellMethod`, it's safe to remove just that command

**Step 3:** Remove or comment the `@ShellMethod` annotation to hide it from CLI without deleting logic:
```java
// @ShellMethod(key = "deploy-terraform", ...)  ← commented out
public String deployTerraform(...) { ... }
```

**Step 4:** If removing the entire feature (including the service class):
1. Remove the `@ShellMethod` handlers from `KnowledgeBotCommands`
2. Remove the service from the constructor parameter list and field
3. Delete the service class file
4. Remove the `@Autowired` dependency from Spring context
5. Build and fix any remaining compile errors

**Never delete a class that is still `@Autowired` somewhere** — the application will fail to start with `NoSuchBeanDefinitionException`.

---

### 10.5 How to Upgrade Java Version

**Current:** Java 21
**Upgrading to:** Java 25 (when stable)

**Step 1:** Update the parent `pom.xml`:
```xml
<properties>
    <java.version>25</java.version>
</properties>
```

**Step 2:** Update your local JDK installation and set `JAVA_HOME`.

**Step 3:** Update `.mvn/wrapper/maven-wrapper.properties` and `mvnw` if needed.

**Step 4:** Check for deprecated APIs. Run:
```bash
./mvnw compile -Xlint:deprecation 2>&1 | grep "warning"
```

**Step 5:** Virtual threads (`Thread.startVirtualThread`) are stable from Java 21+. Java 25 adds structured concurrency — consider refactoring `DAGScheduler` to use `StructuredTaskScope` when upgrading.

---

### 10.6 How to Upgrade Spring Boot Version

**Current:** Spring Boot 3.4.4

**Step 1:** Check the Spring Boot release notes for breaking changes.

**Step 2:** Update `pom.xml`:
```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.5.0</version>  <!-- new version -->
</parent>
```

**Step 3:** Update Spring AI BOM to match — Spring AI versions are tightly coupled to Spring Boot versions. Check https://docs.spring.io/spring-ai/reference/getting-started.html for the compatibility matrix.

**Step 4:** Build and check for deprecation warnings:
```bash
./mvnw clean compile -Xlint:all
```

**Step 5:** Run integration tests and manually test the key flows: `scan`, `chat`, and the web endpoint.

---

### 10.7 How to Upgrade Spring AI Version

**Current:** Spring AI 1.1.0

**Step 1:** Update the BOM version in parent `pom.xml`:
```xml
<properties>
    <spring-ai.version>1.2.0</spring-ai.version>
</properties>
```

**Step 2:** Check Spring AI migration guide — the API surface changes between versions. Key areas that break:
- `SearchRequest` builder API (changed in 1.0)
- `ChatClientRequest`/`ChatClientResponse` types (used by `MetricsAdvisor`)
- `CallAdvisor` / `CallAdvisorChain` interface names

**Step 3:** After upgrading, compile and fix import errors. The `MetricsAdvisor` is the most likely class to need updates since it implements a Spring AI interface directly.

**Step 4:** Re-run the full RAG flow test: `scan` then `chat`.

---

## 11. Best Practices

### Java 21 Specifics

**Use records for immutable data transfer:**
```java
// Good — like ModelDescriptor
public record SummarizeRequest(String filePath, int maxTokens) {}

// Bad — mutable class for simple data
public class SummarizeRequest {
    private String filePath;
    // ... getters/setters
}
```

**Use switch expressions over switch statements:**
```java
// Good — like ModeManager.getModeDescription()
return switch (currentMode.get()) {
    case PLAN -> "PLAN mode: ...";
    case CODE -> "CODE mode: ...";
    case ASK  -> "ASK mode: ...";
};
```

**Text blocks for multi-line strings:**
```java
// Good — like PlanningService.SYSTEM_PROMPT
private static final String PROMPT = """
    You are an expert developer assistant.
    Answer concisely. Use code examples.
    """;
```

### Spring AI Specifics

**Always inject `ChatClient.Builder`, not `ChatClient` directly** — unless you need a specific per-model configuration. The Builder lets Spring manage auto-configuration properly.

**Never call `chatClient.prompt().call()` inside a `Flux` pipeline** — mix blocking calls into reactive streams causes thread starvation. Use `.stream()` for reactive endpoints.

**Monitor token usage** — always call `tokenBudgetService.addTokens(...)` after every AI call so the circuit breaker works correctly.

### AI Integration Rules

1. **Always sanitize** — every AI response that reaches the user must go through `OutputSanitizer.sanitize()`
2. **Always check injection** — every user-provided string must pass `PromptInjectionGuard.isSafe()` before being used in a prompt
3. **Always check mode** — commands that modify files must check `modeManager.canModifyFiles()` first
4. **Always request permission** — destructive actions must call `permissionService.requestPermission()` when safe mode is on
5. **Resolve paths via WorkspaceManager** — never use raw `Paths.get(userInput)` — always `workspaceManager.resolve(userInput)`

---

## 12. Troubleshooting & Common Errors

### Error: `Connection refused: localhost:11434`

**Cause:** Ollama is not running.

**Fix:**
```bash
docker-compose up -d ollama
# Verify it's up:
curl http://localhost:11434/api/tags
```

---

### Error: `404 Not Found` from Ollama

**Cause:** The model specified in `application.yml` hasn't been pulled yet.

**Fix:**
```bash
docker exec -it knowledge-bot-ollama ollama pull llama3.2:3b
docker exec -it knowledge-bot-ollama ollama pull nomic-embed-text
```

**How to see what's pulled:**
```bash
docker exec -it knowledge-bot-ollama ollama list
```

---

### Error: `password authentication failed for user "kbot"`

**Cause:** PostgreSQL container was restarted without proper credential initialization.

**Fix:** Reset the Docker volume:
```bash
docker-compose down -v          # WARNING: deletes all indexed data
docker-compose up -d postgres
```

You'll need to re-index your workspace after this.

---

### Error: `Token budget exhausted`

**Cause:** The session has consumed more than 50,000 tokens (default limit).

**Fix:**
- In CLI: There is currently no `reset-budget` command — you need to restart the application or call `tokenBudgetService.resetSession()` programmatically.
- **To add a reset command**, add this to `KnowledgeBotCommands`:
  ```java
  @ShellMethod(key = "reset-budget", value = "Reset the token budget for a new session")
  public String resetBudget() {
      tokenBudgetService.resetSession();
      return "Token budget reset. New session started.";
  }
  ```

---

### Error: `Cannot switch to CODE mode without attached workspace`

**Cause:** You called `set-mode CODE` before `attach-workspace`.

**Fix:**
```
> attach-workspace C:\your\project\path
> set-mode CODE
```

---

### Error: `NoSuchBeanDefinitionException` on startup

**Cause:** A class annotated with `@Autowired` or constructor injection references a bean that doesn't exist (wrong class name, deleted class, or missing `@Service`/`@Component` annotation).

**Fix:**
1. Read the full error — it tells you exactly which class is missing
2. Check: Is the missing class annotated with `@Service`, `@Component`, or `@Configuration`?
3. Check: Is its package under the base package `com.knowledgebot`? (Otherwise Spring won't scan it)
4. Check: Are all its constructor dependencies also beans?

---

### Error: `Vector dimension mismatch`

**Cause:** You changed the embedding model (e.g. from `nomic-embed-text` 768-dim to another model with different dimensions), but old vectors remain in the database.

**Fix:**
```bash
# WARNING: This deletes all indexed data
docker exec -it knowledge-bot-postgres psql -U kbot -d knowledgebot -c "TRUNCATE global_knowledge_vectors;"
# Update dimensions in application.yml
# Re-index all workspaces
```

---

### Application Starts But `chat` Returns Empty Results

**Cause:** The workspace hasn't been indexed yet — the vector store is empty.

**Fix:**
```
> scan /path/to/your/codebase
# Wait for "Successfully scanned and indexed N files"
> chat your question here
```

---

### Debugging AI Responses

Set logger to TRACE in `application.yml` to see every prompt sent to Ollama:
```yaml
logging:
  level:
    org.springframework.ai: TRACE
    com.knowledgebot: DEBUG
```

This will print:
- The full assembled prompt (system + context + question)
- The raw Ollama HTTP request/response
- The routing decision (which model was selected and why)

---

## Final Notes

### What to Prioritize When Getting Started

1. **Run the system** — get Docker up, pull models, run the web app, test `curl http://localhost:8080/api/v1/chat/simple?prompt=hello`
2. **Study the flow** — trace a single `chat` command from `KnowledgeBotCommands` all the way through to the Ollama call
3. **Understand `ModelRouterService`** — this is the central nervous system
4. **Try the CLI** — run `./mvnw spring-boot:run -pl knowledge-bot-cli` and try every command

### Key Files to Bookmark

| File | Why It Matters |
|---|---|
| `application.yml` | Central configuration — most changes start here |
| `ModelRegistry.java` | All AI model definitions |
| `ModelRouterService.java` | Routing logic |
| `KnowledgeBotCommands.java` | All CLI commands |
| `ChatController.java` | All REST endpoints |
| `EmbeddingPipeline.java` | How files become searchable |
| `RetrievalService.java` | How questions find relevant code |

### The Golden Rule

> When in doubt: **search before modifying**. Use `grep -r "ClassName" --include="*.java" .` to find all usages of any class before changing or deleting it. The dependency chain in a multi-module project means a change in `knowledge-bot-core` can break `knowledge-bot-web` in non-obvious ways.

---

*Guide generated: 2026-04-06 | Based on codebase version: 1.0.0-SNAPSHOT*
