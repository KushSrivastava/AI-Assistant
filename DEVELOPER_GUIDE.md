# Knowledge-Bot: Complete Developer Onboarding Guide

> **Who is this for?** You are a junior Java developer taking full ownership of this project. You understand basic Java but have never seen this codebase before. This guide will give you the complete picture: what every piece does, why it was built that way, and how to extend, modify, or safely remove any part of it. Read it end to end at least once before touching any code.

---

## Table of Contents

1. [What is Knowledge-Bot?](#1-what-is-knowledge-bot)
2. [High-Level Architecture & System Flow](#2-high-level-architecture--system-flow)
3. [Module Map: What Lives Where](#3-module-map-what-lives-where)
4. [Core Code Logic & Class Deep-Dive](#4-core-code-logic--class-deep-dive)
   - 4.1 [Model Layer — Choosing the Right AI](#41-model-layer--choosing-the-right-ai)
   - 4.2 [Orchestration Layer — Planning & Executing Tasks](#42-orchestration-layer--planning--executing-tasks)
   - 4.3 [RAG Pipeline — Teaching the Bot About Your Codebase](#43-rag-pipeline--teaching-the-bot-about-your-codebase)
   - 4.4 [Data Layer — Storing Knowledge](#44-data-layer--storing-knowledge)
   - 4.5 [Core Services — Utilities Used Everywhere](#45-core-services--utilities-used-everywhere)
   - 4.6 [CLI & Web Entry Points](#46-cli--web-entry-points)
   - 4.7 [MCP Layer — Tool Interoperability](#47-mcp-layer--tool-interoperability)
5. [How-To Extension Guide](#5-how-to-extension-guide)
   - 5.1 [Adding a New AI Model](#51-adding-a-new-ai-model)
   - 5.2 [Adding a New CLI Command](#52-adding-a-new-cli-command)
   - 5.3 [Adding a New REST Endpoint](#53-adding-a-new-rest-endpoint)
   - 5.4 [Adding a New Agent Tool](#54-adding-a-new-agent-tool)
   - 5.5 [Safely Removing a Feature](#55-safely-removing-a-feature)
   - 5.6 [Updating Spring AI Version](#56-updating-spring-ai-version)
6. [Best Practices & Troubleshooting](#6-best-practices--troubleshooting)
7. [Zero-Assumptions Local Setup Guide](#7-zero-assumptions-local-setup-guide)
8. [Environment Variables Reference](#8-environment-variables-reference)
9. [Gotchas & Common Errors](#9-gotchas--common-errors)

---

## 1. What is Knowledge-Bot?

Knowledge-Bot is a **context-aware AI assistant for developers**. It does not just answer generic questions like ChatGPT. It reads your project's source code, understands it, stores that understanding in a database, and then answers questions specifically about your codebase. It can:

- Answer questions like "How does the login flow work in this project?"
- Generate new code that follows your project's existing patterns
- Review pull requests using knowledge of your coding standards
- Execute multi-step agentic tasks (e.g., "refactor all services to use the new pattern") by breaking the work into a plan and executing each step
- Route queries to the cheapest/fastest AI model that can handle the task complexity
- Run entirely locally (no internet required) using Ollama, or use OpenAI for complex tasks

### The Big Picture in One Sentence

> A user types a question or command → the bot finds the most relevant parts of the codebase from a vector database → injects that context into a prompt → sends it to the best AI model available → returns a grounded, codebase-specific answer.

This pattern is called **RAG: Retrieval-Augmented Generation**. It is the central design principle of the entire system.

---

## 2. High-Level Architecture & System Flow

### Infrastructure Layer

Two services must be running (via Docker) before any Java code starts:

```
┌─────────────────────────────────────────────────────┐
│                   Docker Compose                    │
│                                                     │
│  ┌────────────────────┐  ┌─────────────────────┐   │
│  │  Ollama :11434     │  │  PostgreSQL :5432   │   │
│  │  (local LLMs)      │  │  + pgvector ext.    │   │
│  │                    │  │                     │   │
│  │  deepseek-coder    │  │  DB: knowledgebot   │   │
│  │  llama3.2:3b       │  │  User: kbot         │   │
│  │  nomic-embed-text  │  │  Table: global_     │   │
│  └────────────────────┘  │  knowledge_vectors  │   │
│                          └─────────────────────┘   │
└─────────────────────────────────────────────────────┘
```

**Ollama** is a local server that runs open-source language models on your machine. You talk to it over HTTP at `localhost:11434`. No API key, no internet, no per-token cost.

**PostgreSQL with pgvector** is a regular Postgres database with the `vector` extension installed. This extension lets you store 768-dimensional floating-point vectors (the mathematical representation of text meaning) and search them efficiently. The `pgvector/pgvector:pg17` Docker image already has the extension installed.

### Application Layer

```
┌────────────────────────────────────────────────────────────────────┐
│                        Java Application                            │
│                                                                    │
│  Entry Points:                                                     │
│  ┌──────────────────┐    ┌───────────────────────────────────┐    │
│  │  knowledge-bot-  │    │  knowledge-bot-web                │    │
│  │  cli             │    │  Spring Boot :8080                │    │
│  │  Spring Shell    │    │  REST API (ChatController)        │    │
│  │  (interactive    │    │                                   │    │
│  │   terminal)      │    └───────────────────────────────────┘    │
│  └────────┬─────────┘                      │                      │
│           │                                │                      │
│           └──────────────┬─────────────────┘                      │
│                          ▼                                         │
│  ┌────────────────────────────────────────────────────────────┐   │
│  │  knowledge-bot-ai  (the brain)                             │   │
│  │                                                            │   │
│  │  ModelRouterService ──► ChatClientFactory                  │   │
│  │        │                      │                            │   │
│  │        │              OllamaChatModel / OpenAiChatModel    │   │
│  │        │                                                   │   │
│  │  OrchestratorService ──► DAGScheduler ──► virtual threads  │   │
│  │        │                                                   │   │
│  │  PlanningService ──► TrackedPlan                           │   │
│  │  EmbeddingPipeline ──► VectorStore (PGVector)              │   │
│  │  RetrievalService ──► HybridSearchService                  │   │
│  └────────────────────────────────────────────────────────────┘   │
│                          │                                         │
│           ┌──────────────┴──────────────┐                         │
│           ▼                             ▼                          │
│  ┌─────────────────┐         ┌────────────────────────┐           │
│  │ knowledge-bot-  │         │ knowledge-bot-data      │           │
│  │ core            │         │                         │           │
│  │ (utilities,     │         │ GlobalKnowledgeService  │           │
│  │  scanning,      │         │ ConflictResolutionSvc   │           │
│  │  security)      │         │ WorklogService          │           │
│  └─────────────────┘         │ VectorStoreConfig       │           │
│                              └────────────────────────┘           │
└────────────────────────────────────────────────────────────────────┘
```

### End-to-End Request Flow (Ask Command)

Here is what happens step by step when you type `ask "How does authentication work?"` in the CLI:

```
1. KnowledgeBotCommands.ask("How does authentication work?")
        │
        ▼
2. ModelRouterService.selectModel(prompt)
   → IntentClassifier determines: SIMPLE complexity
   → Selects "local-fast" (llama3.2:3b) based on score
        │
        ▼
3. RetrievalService.retrieve(query, topK=5)
   → HybridSearchService.hybridSearch(query, 5)
       ├─ VectorStore.similaritySearch(query)     ← semantic match
       └─ JdbcTemplate SQL full-text search       ← keyword match
   → Returns top 5 most relevant documents
        │
        ▼
4. ContextPruningService.prune(docs, tokenBudget)
   → Trims documents to fit within model token limit
        │
        ▼
5. PromptCompositionService.compose(query, context)
   → Builds final prompt: system instructions + context + question
        │
        ▼
6. ChatClient.prompt(finalPrompt).call().content()
   → MetricsAdvisor measures latency of this call
   → ModelMetrics updated with EMA latency
        │
        ▼
7. Answer returned to terminal
```

---

## 3. Module Map: What Lives Where

The project is split into 8 Maven modules. Each module is a separate `pom.xml` directory. Think of them as separate libraries that depend on each other.

```
knowledge-bot-parent/           ← root pom.xml, sets versions for everything
├── knowledge-bot-common/       ← shared data classes (DTOs, enums, records)
├── knowledge-bot-core/         ← stateless utilities (scanning, security, search)
├── knowledge-bot-data/         ← database access (vector store, conflict resolution)
├── knowledge-bot-ai/           ← all AI logic (models, agents, orchestration, RAG)
├── knowledge-bot-web/          ← Spring Boot web app (REST API on port 8080)
├── knowledge-bot-cli/          ← Spring Shell CLI (interactive terminal)
└── knowledge-bot-mcp/          ← MCP server (tool interoperability protocol)
```

**Dependency direction (important):** Dependencies only flow in one direction. `knowledge-bot-ai` depends on `knowledge-bot-core` and `knowledge-bot-data`. Nothing in `knowledge-bot-core` should ever import from `knowledge-bot-ai`. This keeps the utilities testable in isolation.

```
common ← core ← data ← ai ← web
                       ai ← cli
              core ← mcp
```

### Why This Structure?

This separation exists for a practical reason: the AI logic changes frequently as models evolve, but the core utilities (file scanning, security checks, token counting) are stable. By isolating them, you can update the AI layer without risking breaking the utilities.

---

## 4. Core Code Logic & Class Deep-Dive

This section explains every major class: what it does, why it was built that way, and what you should know before modifying it.

---

### 4.1 Model Layer — Choosing the Right AI

The model layer lives in `knowledge-bot-ai/src/main/java/com/knowledgebot/ai/model/`.

#### `ModelDescriptor.java` — The Data Model for a Model

```
knowledge-bot-ai/.../model/ModelDescriptor.java
```

This is a Java `record` (an immutable data class) that describes one AI model. Every registered model has one.

**Fields:**
- `modelName` — the name passed to the API (e.g., `"llama3.2:3b"`, `"gpt-4o"`)
- `provider` — `ModelProvider.OLLAMA` or `ModelProvider.OPENAI`
- `minComplexity` / `maxComplexity` — the complexity range this model handles
- `costPer1kTokens` — 0.0 for local models, ~0.01 for OpenAI
- `maxContextTokens` — the maximum tokens the model can accept in one call
- `recommendedPromptBudget` — a safe limit to leave room for the response
- `purpose` — human-readable description

**Why a record?** Records are immutable by design in Java 16+. A model descriptor should never change at runtime. Using a record instead of a class prevents accidental mutation and removes boilerplate getter code.

#### `ModelRegistry.java` — The Catalogue of Available Models

```
knowledge-bot-ai/.../model/ModelRegistry.java
```

A `@Service` that reads from `application.yml` and constructs `ModelDescriptor` instances. It holds four models by default:
- `local-fast` — `llama3.2:3b` for simple tasks
- `local-code` — `deepseek-coder-v2:16b` for code tasks
- `local-fallback` — `llama3.2:3b` as safety net
- `cloud-reasoning` — `gpt-4o` for complex multi-step tasks
- `cloud-creative` — `gpt-4o` for creative/detailed tasks

**Why not hardcode models?** All model names and parameters come from `application.yml`, not the Java code. This means you can swap out `deepseek-coder-v2:16b` for a newer model by changing a config file — no recompile needed.

#### `TaskComplexity.java` — The Classification Enum

Three levels: `SIMPLE`, `MODERATE`, `COMPLEX`, `REASONING_HEAVY`. This is how the router decides which model to use.

#### `IntentClassifier.java` — Deciding Complexity from Text

A `@Service` that reads a prompt string and returns a `TaskComplexity`. It uses keyword matching and heuristics:
- Short prompts, single-step questions → `SIMPLE`
- Code generation, multi-file references → `MODERATE`
- Architecture decisions, multi-step plans → `COMPLEX`

**Why not use an AI model to classify?** That would be circular (using an AI to decide which AI to use) and adds latency before every request. Simple regex/keyword classification is fast and predictable.

#### `ModelRouterService.java` — The Smart Router

```
knowledge-bot-ai/.../model/ModelRouterService.java
```

This is one of the most important services. It decides which AI model handles each request.

**The Selection Algorithm:**
```
1. Classify the prompt → TaskComplexity
2. Filter all models to those whose complexity range includes this complexity
3. If only one candidate → return it immediately
4. Normalise latency and cost across all candidates (0.0–1.0 scale)
5. Score each candidate:
       score = (0.5 × normLatency) + (0.5 × normCost) + errorPenalty
6. Return the model with the LOWEST score (best performance/cost balance)
```

**Why EMA (Exponential Moving Average) for latency?** Raw latency spikes (e.g., a slow network blip) would permanently hurt a model's score. EMA gives more weight to recent calls and gradually forgets old outliers. The formula is:
```
newAvg = α × newSample + (1 - α) × oldAvg
```
With α = 0.2, each new measurement contributes 20% and old history contributes 80%. After ~10 calls, the average is stable.

**The `@PostConstruct init()` method:** This runs once after Spring creates the bean. It seeds `ModelMetrics` entries for every model and shares the metrics map with `ChatClientFactory`. It also schedules a background thread to log a health snapshot every 60 seconds.

**The `@Scheduled` re-evaluation:** The `ScheduledExecutorService` logs current metrics every 60 seconds. In a future version, this is where you could implement circuit-breaking (e.g., temporarily stop routing to a model that has >50% error rate).

#### `ModelMetrics.java` — Per-Model Performance Tracking

```
knowledge-bot-ai/.../model/ModelMetrics.java
```

Tracks three things per model:
- `avgLatencyMs` — EMA of response times
- `errorCount` — total errors (never reset)
- `totalCalls` — total calls made

The `computeScore(normLatency, normCost)` method combines latency and cost equally, then adds an error penalty:
```java
errorRate = errorCount / max(totalCalls, 1)
score = 0.5 × normLatency + 0.5 × normCost + (errorRate × 2.0)
```

The `× 2.0` multiplier means even a 10% error rate adds 0.2 to the score, which is significant enough to prefer other models.

#### `MetricsAdvisor.java` — Measuring Every AI Call

```
knowledge-bot-ai/.../model/MetricsAdvisor.java
```

This implements Spring AI's `CallAdvisor` interface. Think of it as middleware that wraps every AI call. It:
1. Records the start time
2. Calls `chain.nextCall()` (the actual AI request)
3. On success: calculates elapsed time → calls `metrics.recordSuccess(latencyMs)`
4. On exception: calls `metrics.recordError()` then re-throws

**Why `LOWEST_PRECEDENCE` order?** Advisors in Spring AI run in order. By being last (lowest precedence = runs outermost), `MetricsAdvisor` measures the total round-trip time including all other advisors, retries, and the actual HTTP call. This gives the most accurate "wall clock" latency from the user's perspective.

**Where does it get attached?** In `ChatClientFactory.buildClient()`, every `ChatClient` gets `.defaultAdvisors(advisor)` added during construction. So every call through any `ChatClient` in this system is automatically measured.

#### `ChatClientFactory.java` — Building AI Clients

```
knowledge-bot-ai/.../model/ChatClientFactory.java
```

This factory creates `ChatClient` instances (Spring AI's main interface for talking to models).

**Key design decision:** Spring AI 1.1.0 removed the ability to manually construct `OllamaApi` and `OpenAiApi` objects. Instead, Spring Boot auto-configures `OllamaChatModel` and `OpenAiChatModel` beans from `application.yml`. The factory receives these pre-built beans via constructor injection and wraps them into `ChatClient` instances using the builder pattern.

**The `setMetricsMap()` method:** Called by `ModelRouterService` during `@PostConstruct`. This is how the factory knows which `ModelMetrics` object to attach to each advisor. The dependency flows: `ModelRouterService` owns the map → shares it with the factory → factory creates advisors that write into the map.

---

### 4.2 Orchestration Layer — Planning & Executing Tasks

The orchestration layer lives in `knowledge-bot-ai/src/main/java/com/knowledgebot/ai/orchestration/` and `.../planning/`.

#### `PlanningService.java` — Generating Multi-Step Plans

```
knowledge-bot-ai/.../planning/PlanningService.java
```

Given a user's goal (e.g., "Add JWT authentication to all endpoints"), this service asks the AI to decompose it into a numbered markdown list of steps. The response looks like:
```
1. Analyze existing security configuration
2. Add spring-security-oauth2 dependency
3. Create JwtTokenProvider class
...
```

The `parseSteps()` static method extracts these steps using a regex (`TASK_LINE` pattern matching lines that start with `1.`, `2.`, etc.).

**Why markdown format?** The plan is stored and displayed as markdown. The same text that the AI generates can be shown in the terminal, saved to a file, and parsed programmatically. One format serves all purposes.

#### `TrackedPlan.java` — Live Plan Progress

```
knowledge-bot-ai/.../planning/TrackedPlan.java
```

A wrapper around the plan that tracks the status of each step. Each `Step` has an `AtomicReference<StepStatus>` that can be:
```
NOT_STARTED → IN_PROGRESS → COMPLETED
                          → FAILED
                          → BLOCKED
```

**Why `AtomicReference`?** The `DAGScheduler` runs tasks on virtual threads concurrently. Multiple threads may try to update a step's status at the same time. `AtomicReference` is a thread-safe wrapper that guarantees only one thread's update wins, without needing `synchronized` blocks or locks.

The `renderProgressBoard()` method produces output like:
```
Goal: Add JWT authentication
Progress: 3/5 (60%)
[✓] Step 1: Analyze existing security configuration
[▶] Step 2: Add dependency (IN_PROGRESS)
[✗] Step 3: Create JwtTokenProvider (FAILED)
[ ] Step 4: Write tests
[ ] Step 5: Update documentation
```

#### `DagTask.java` — A Single Unit of Work in a Graph

```
knowledge-bot-ai/.../orchestration/DagTask.java
```

A `record` representing one task node in the execution graph. Fields:
- `id` — unique string identifier
- `description` — the task prompt text
- `dependencies` — list of task IDs that must complete before this can start
- `status` — `AtomicReference<TaskStatus>` (mutable, thread-safe)

**Why a DAG (Directed Acyclic Graph)?** Many tasks are independent of each other. If step 1 is "write unit tests" and step 2 is "update the README", these can run at the same time. A DAG captures which tasks have dependencies and which can run in parallel, enabling the system to execute independent tasks concurrently using virtual threads.

**`isReady()`:** Returns `true` if all dependency tasks have status `COMPLETED`. This is polled by the scheduler to decide when to start a task.

**`hasFailedDependencies()`:** Returns `true` if any dependency has `FAILED`. The scheduler marks this task `BLOCKED` without executing it.

#### `DAGScheduler.java` — Running Tasks Concurrently

```
knowledge-bot-ai/.../orchestration/DAGScheduler.java
```

This is the execution engine. It:
1. Takes a list of `DagTask` objects with their dependency graph
2. Finds all tasks that are ready (dependencies completed)
3. Submits each ready task to a virtual thread pool
4. Polls for completion, then finds the next wave of ready tasks
5. Repeats until all tasks are done or the timeout expires

**Virtual Threads:** Java 21's Project Loom feature. Unlike traditional threads (limited to ~1000 concurrent threads), virtual threads are lightweight fibers managed by the JVM. You can create thousands of them. Each task gets its own virtual thread, making waiting for AI responses (which is I/O-bound) essentially free in terms of system resources.

**Stuck-State Detection:** Before marking a task complete, the scheduler compares the new result with recent results using `StuckStateDetector`. If the AI keeps producing the same output (a sign it is looping or confused), the scheduler:
1. Increments a retry counter
2. Rewrites the prompt to include a recovery instruction ("You seem to be stuck. Try a completely different approach...")
3. Re-executes the task, up to `MAX_RETRIES_PER_TASK = 3` times

**`buildGraphFromPlan(markdownPlan)`:** Parses the markdown plan text, creates `DagTask` objects, and assigns sequential dependencies (each task depends on the previous). This is the simple case.

**`buildGraphWithParallelTasks(independent, sequential)`:** Creates independent tasks with no dependencies between them (they all run simultaneously), then adds sequential tasks that depend on all independent tasks completing first.

#### `StuckStateDetector.java` — Detecting Repetitive AI Output

```
knowledge-bot-ai/.../orchestration/StuckStateDetector.java
```

Uses a ring buffer (a fixed-size `ArrayDeque`) to store the last N results. When a new result arrives:
1. Normalize it (lowercase, trim whitespace)
2. Compare it to each stored result using Levenshtein edit distance
3. If similarity > 90% to any stored result → `isStuck() = true`

**Levenshtein Distance:** The number of single-character edits (insertions, deletions, substitutions) needed to transform one string into another. Two strings are considered "the same" if the edit distance is less than 10% of the longer string's length.

**Two-row DP optimization:** The `editDistance()` method uses only two arrays instead of a full matrix, keeping memory usage at O(min(m, n)) instead of O(m × n). For responses that might be several hundred characters long, this is meaningful.

#### `OrchestratorService.java` — The Top-Level Coordinator

```
knowledge-bot-ai/.../orchestration/OrchestratorService.java
```

The entry point for running any multi-step plan. It:
1. Calls `PlanningService.parseSteps()` to extract steps from the plan markdown
2. Creates a `TrackedPlan` for live progress tracking
3. Calls `DAGScheduler.buildGraphFromPlan()` to build the execution graph
4. Calls `DAGScheduler.executeAll()` to run everything concurrently
5. Calls `syncStatusesToPlan()` to map DAG outcomes back to plan step statuses
6. Calls `KnowledgeMerger.mergeResults()` to combine all task outputs
7. Sends notifications via `NotificationService`

The `activePlan` field is an `AtomicReference<TrackedPlan>` — again, thread-safe because the CLI's `plan-status` command might read the plan while execution is in progress.

---

### 4.3 RAG Pipeline — Teaching the Bot About Your Codebase

This is the indexing and retrieval system that makes the bot aware of your codebase.

#### `WorkspaceScannerService.java` — Finding Files

```
knowledge-bot-core/.../scanner/WorkspaceScannerService.java
```

Walks a directory tree recursively and returns all files that match allowed extensions (`.java`, `.py`, `.md`, `.txt`, `.yaml`, `.json`, etc.) while excluding common noise directories (`node_modules`, `.git`, `target`, `build`).

**Why exclude `target/`?** Maven compiles `.java` sources to `.class` files in the `target/` directory. Indexing compiled bytecode instead of source code would produce meaningless vectors. Always index source, never compiled output.

#### `SemanticIndexingService.java` — Understanding Java Code Structure

```
knowledge-bot-core/.../scanner/SemanticIndexingService.java
```

Uses **JavaParser** (a library that parses Java source code into an Abstract Syntax Tree) to extract structured information from `.java` files:

**What it extracts:**
- Package name and imports
- Class/interface/enum declarations with modifiers
- Field names and types
- Method signatures (name, return type, parameters) — not the method bodies
- A call graph: which methods call which other methods
- An import graph: which external packages this file depends on

**Why not just index the raw source?** Raw source contains implementation noise (variable names, loop variables, comments). The structured summary focuses on the API surface: what the class is, what methods it exposes, and what it depends on. This produces much better vector embeddings for searching.

**Fallback behavior:** If JavaParser fails (e.g., the file has syntax errors), the service falls back to returning the raw source text. The embedding still gets stored, just with less structure.

#### `EmbeddingPipeline.java` — Converting Files to Vectors

```
knowledge-bot-ai/.../pipeline/EmbeddingPipeline.java
```

The conversion pipeline that turns a file into a vector stored in PostgreSQL.

**For `.java` files:**
```
Source file → SemanticIndexingService → structured summary text
                                     → call graph appended
                                     → import graph appended
                                     → Document(combinedText, metadata)
                                     → VectorStore.add()
```

**For supported formats (PDF, DOCX, etc.):**
```
File → Apache Tika → raw text → Document → VectorStore.add()
```

**Apache Tika** is a content analysis library that can extract text from 1000+ file formats. You feed it a file and it gives you plain text.

**For everything else:** Read as UTF-8 text directly.

**The `Document` class (Spring AI):** A wrapper around a `String content` and a `Map<String, Object> metadata`. The metadata holds the file path, filename, type, etc. When documents are retrieved from the vector store later, this metadata tells you which file the matching text came from.

#### `IndexingPipeline.java` — The Indexing Coordinator

```
knowledge-bot-core/.../scanner/IndexingPipeline.java
```

Coordinates the full re-indexing process:
1. `WorkspaceScannerService.scanWorkspace()` → list of file paths
2. `EmbeddingPipeline.buildDocument()` for each file → `Document`
3. `VectorStore.add(documents)` → stored in PostgreSQL

`checkAndReindex()` is the smart version: it checks if Git is available (via `EnvironmentSensingService`) and logs that git-diff-based incremental re-indexing could be added in a future version. Currently it always does a full re-index.

#### `HybridSearchService.java` — Finding Relevant Documents

```
knowledge-bot-core/.../retrieval/HybridSearchService.java
```

Implements two complementary search strategies and merges their results:

**Semantic Search (Vector/Cosine Similarity):**
The user's query is embedded into a vector by the embedding model (`nomic-embed-text`). Then pgvector finds the stored document vectors that are closest in angular distance (cosine similarity). This finds conceptually similar content even if the exact words differ (e.g., "authentication" and "login" are semantically close).

**Keyword Search (PostgreSQL Full-Text Search):**
Uses Postgres's built-in `to_tsvector` / `plainto_tsquery` functions. This finds documents that contain the exact words from the query. It catches cases where semantic search misses specific identifiers (class names, method names, error codes).

**Merging (Reciprocal Rank Fusion):**
Both result sets are combined into a `Map<String, Document>` keyed by document ID. Duplicates are deduplicated — if a document appears in both semantic and keyword results, it appears only once in the final list. The top `topK` documents are returned.

**Why both strategies?** Neither is perfect alone. Semantic search understands meaning but can return loosely related content. Keyword search is precise but misses synonyms and paraphrasing. Together they achieve higher recall and precision.

#### `RetrievalService.java` — The RAG Interface

```
knowledge-bot-ai/.../retrieval/RetrievalService.java
```

The AI module's entry point for retrieval. It delegates to `HybridSearchService` and then applies context pruning: if the retrieved documents are too large for the model's token budget, `ContextPruningService` trims them.

---

### 4.4 Data Layer — Storing Knowledge

#### `VectorStoreConfig.java` — Configuring the Vector Database

```
knowledge-bot-data/.../config/VectorStoreConfig.java
```

Spring `@Configuration` class that sets up the pgvector store. The key settings (from `application.yml`):
- `dimensions: 768` — must match the embedding model's output size (`nomic-embed-text` produces 768-dimensional vectors)
- `index-type: HNSW` — Hierarchical Navigable Small World graph index; fast approximate nearest-neighbor search
- `distance-type: COSINE_DISTANCE` — measures angular similarity (two vectors pointing in the same direction = similar meaning, regardless of magnitude)
- `initialize-schema: true` — Spring AI creates the `global_knowledge_vectors` table automatically on first run

**WARNING:** If you change the embedding model to one that produces a different vector dimension (e.g., a model that produces 1536 dimensions), you must:
1. Change `dimensions:` in `application.yml`
2. Drop and recreate the `global_knowledge_vectors` table (all existing vectors are incompatible)

#### `GlobalKnowledgeService.java` — Cross-Project Knowledge

```
knowledge-bot-data/.../memory/GlobalKnowledgeService.java
```

Stores and retrieves knowledge that should persist across all projects — best practices, team conventions, architecture decisions. Uses the same vector store as per-project knowledge but stores them with different metadata tags so they can be filtered separately.

#### `ConflictResolutionService.java` — Handling Contradictory Knowledge

```
knowledge-bot-data/.../memory/ConflictResolutionService.java
```

When two stored documents say contradictory things (e.g., "use camelCase" vs "use snake_case"), this service uses an AI model to resolve which is more authoritative. It presents both documents and asks the model to determine which is correct, then updates the store accordingly.

#### `WorklogService.java` — Tracking What the Bot Did

```
knowledge-bot-data/.../worklog/WorklogService.java
```

Maintains a persistent log of every action the bot takes (what command was run, what the input was, what was returned). This enables:
- Audit trail for debugging
- "What did I ask the bot yesterday?" queries
- Future training data collection

---

### 4.5 Core Services — Utilities Used Everywhere

#### `TokenBudgetService.java` — Counting Tokens

```
knowledge-bot-core/.../performance/TokenBudgetService.java
```

Estimates the number of tokens in a text string. Large Language Models have a maximum context window (e.g., 32768 tokens). Exceeding it causes the request to fail with an error. This service:
- `estimateTokens(text)` — approximates token count (roughly 4 characters per token for English text)
- `willFitInContext(text, model)` — returns `true` if the text fits within the model's context window

**Why estimate instead of count exactly?** Exact token counting requires running the model's tokenizer (a piece of software that splits text the same way the model does). This is fast for some models but requires downloading tokenizer files. The approximation is close enough for the budget decisions made here.

#### `ContextPruningService.java` — Trimming Overflowing Context

```
knowledge-bot-ai/.../prompt/ContextPruningService.java
```

When retrieved documents together exceed the model's token budget, this service trims them. Strategy:
1. Sort documents by relevance score
2. Add documents one by one until the budget would be exceeded
3. Stop and return only the fitting documents

The goal is to always send the most relevant documents that fit, rather than sending everything and crashing.

#### `PromptCompositionService.java` — Building the Final Prompt

```
knowledge-bot-ai/.../prompt/PromptCompositionService.java
```

Assembles the final prompt that gets sent to the AI model:
```
[SYSTEM]
You are an expert code assistant. Use the following context from the codebase to answer the question.
Only answer based on the provided context. If you don't know, say so.

[CONTEXT]
--- File: src/main/java/com/example/AuthService.java ---
(file contents from vector search)

--- File: src/main/java/com/example/UserRepository.java ---
(file contents from vector search)

[QUESTION]
How does authentication work?
```

This three-part structure (system instructions + retrieved context + user question) is the standard RAG prompt pattern.

#### `EnvironmentSensingService.java` — Detecting Available Tools

```
knowledge-bot-core/.../scanner/EnvironmentSensingService.java
```

Detects what tools are available on the host machine by trying to run version commands (`git --version`, `mvn --version`, `docker --version`, etc.). Stores results in a `Map<String, String>` of tool name → version.

**Why detect tools?** The bot can do more when certain tools are present. For example, if Git is available, the bot can analyze git history and diffs. If Docker is available, it can run tests in isolated containers. This service lets the rest of the system make decisions based on the actual environment.

#### `CommandPermissionService.java` — Safety Checks Before Running Commands

```
knowledge-bot-core/.../security/CommandPermissionService.java
```

Maintains a whitelist of allowed shell commands. Before the bot executes any system command (e.g., to run tests), this service checks if the command is on the allowlist. Commands like `rm -rf`, `curl | sh`, or anything with pipes to potentially dangerous tools are blocked.

**Why this matters:** If the bot's AI model generates a malicious command (via prompt injection or hallucination), this is the last line of defense before it runs on your machine.

#### `PromptInjectionGuard.java` — Detecting Malicious Inputs

```
knowledge-bot-core/.../security/PromptInjectionGuard.java
```

Scans user inputs for prompt injection patterns — attempts to override the system prompt (e.g., "Ignore all previous instructions and..."). If detected, the input is rejected before it reaches the AI model.

#### `OutputSanitizer.java` — Cleaning AI Responses

```
knowledge-bot-core/.../security/OutputSanitizer.java
```

Scans AI model outputs before they are displayed or executed. Strips:
- Hidden Unicode characters (used in some prompt injection attacks)
- Overly long lines that might indicate data exfiltration attempts
- Code blocks containing known dangerous patterns

#### `AgentTraceInterceptor.java` — Distributed Tracing

```
knowledge-bot-core/.../observability/AgentTraceInterceptor.java
```

Uses Micrometer Tracing to attach trace IDs and spans to every operation. When the bot performs a complex multi-step plan, each step gets its own span. This lets you see in logs exactly how long each part took:
```
TraceID: abc123 | Span: planGeneration     | 1.2s
TraceID: abc123 | Span: taskExecution[0]   | 8.4s
TraceID: abc123 | Span: taskExecution[1]   | 5.1s
TraceID: abc123 | Span: knowledgeMerge     | 0.3s
```

#### `CostTokenTracker.java` — Tracking Token Usage and Cost

```
knowledge-bot-core/.../observability/CostTokenTracker.java
```

Records how many tokens each request uses and calculates estimated cost (for OpenAI calls). Provides a summary report showing total tokens consumed and total estimated cost since startup.

---

### 4.6 CLI & Web Entry Points

#### `KnowledgeBotCommands.java` — The Interactive Terminal

```
knowledge-bot-cli/.../KnowledgeBotCommands.java
```

This is the primary user interface — a Spring Shell application. Each `@ShellMethod`-annotated method becomes a terminal command.

**Available commands:**
| Command | What it does |
|---|---|
| `ask "<question>"` | RAG-based Q&A against indexed codebase |
| `index <path>` | Index a directory into the vector store |
| `watch <path>` | Watch a directory and re-index on file changes |
| `generate "<task>"` | Generate code for a described task |
| `review-pr <number>` | Review a GitHub pull request |
| `plan "<goal>"` | Generate and execute a multi-step plan |
| `plan-status` | Show live progress of the active plan |
| `status` | Show model routing metrics and system status |
| `semantic-analyze <path>` | Show JavaParser analysis of a Java file |
| `mode <name>` | Switch between bot modes (code, research, etc.) |

**How Spring Shell works:** You annotate methods with `@ShellMethod("description")`. Spring Shell discovers these at startup and makes them available as commands in the interactive terminal. The method parameters are the command arguments. Return a `String` and it gets printed to the terminal.

#### `ChatController.java` — The REST API

```
knowledge-bot-web/.../web/ChatController.java
```

A standard Spring MVC `@RestController` exposing the bot functionality as HTTP endpoints. The main endpoint accepts a POST request with a JSON body containing the user's query, processes it through the same RAG pipeline as the CLI, and returns the answer as JSON.

**Why both CLI and REST API?** Different use cases need different interfaces. A developer working locally prefers the CLI. An integration with another service (e.g., a Slack bot, a VS Code extension) needs a REST API. Both use the same underlying AI logic.

---

### 4.7 MCP Layer — Tool Interoperability

The MCP (Model Context Protocol) module lives in `knowledge-bot-mcp/`.

#### What is MCP?

MCP is a JSON-RPC protocol invented by Anthropic that allows AI assistants to discover and call external tools. The idea is: instead of hardcoding tool integrations into each AI assistant, you build a "tool server" that exposes tools via MCP, and any MCP-compatible AI client can discover and use them.

Think of it as USB for AI tools: one standard interface, many compatible devices.

#### `McpToolRegistry.java` — Registering Available Tools

Maintains a list of `McpToolDefinition` objects. Each definition describes a tool: its name, description, and parameters. The AI model can query this registry to discover what tools are available.

#### `McpDomainServer.java` — The MCP Server

Exposes the registered tools over a JSON-RPC HTTP endpoint. External AI agents (like Claude Desktop, Cursor, or other MCP-compatible clients) can point to this server's URL and use the bot's tools.

#### `McpClientService.java` — Calling External MCP Tools

The flip side: this service lets the bot itself call tools on external MCP servers. So the bot can use tools from other systems (e.g., a database tool from another MCP server) without needing custom integration code.

---

## 5. How-To Extension Guide

### 5.1 Adding a New AI Model

Example: You want to add **Mistral 7B** running on Ollama.

**Step 1: Pull the model in Ollama**
```bash
ollama pull mistral:7b
```

**Step 2: Add configuration to `application.yml`**

In `knowledge-bot-web/src/main/resources/application.yml`, add under `knowledge-bot.model-routing`:
```yaml
knowledge-bot:
  model-routing:
    # ... existing entries ...
    local-mistral-provider: OLLAMA
    local-mistral-model: mistral:7b
    local-mistral-min: SIMPLE
    local-mistral-max: MODERATE
    local-mistral-cost: 0.0
    local-mistral-tokens: 32000
    local-mistral-purpose: "General purpose local model"
    local-mistral-context: 32768
    local-mistral-budget: 16000
```

**Step 3: Register the model in `ModelRegistry.java`**

In `ModelRegistry.java`, add a new `@Value`-annotated field for each new config property, and add the model to the `getAllModels()` list:
```java
@Value("${knowledge-bot.model-routing.local-mistral-model}")
private String localMistralModel;

// ... add similar fields for all new properties ...

// In the getAllModels() method, add:
new ModelDescriptor(
    "local-mistral",
    ModelProvider.OLLAMA,
    localMistralModel,
    TaskComplexity.SIMPLE,    // minComplexity
    TaskComplexity.MODERATE,  // maxComplexity
    0.0,                      // cost
    32000,                    // maxTokens
    16000,                    // promptBudget
    "General purpose local model",
    32768                     // contextWindow
)
```

**Step 4: Recompile and test**
```bash
./mvnw compile
./mvnw spring-boot:run -pl knowledge-bot-cli
# In shell:
status   # should show local-mistral in the metrics table
ask "Hello, which model are you?"
```

**No code changes needed in:** `ModelRouterService`, `ChatClientFactory`, `IntentClassifier`, `MetricsAdvisor`. All of these work automatically for any model in the registry.

---

### 5.2 Adding a New CLI Command

Example: You want a `summarize <path>` command that summarizes a file.

**Step 1: Add the method to `KnowledgeBotCommands.java`**

```java
@ShellMethod("Summarize a file using AI")
public String summarize(@ShellOption(help = "Path to the file to summarize") String filePath) {
    try {
        Path path = Path.of(filePath);
        String content = Files.readString(path);

        ChatClient client = modelRouterService.getClientForComplexity(TaskComplexity.SIMPLE);
        return client.prompt()
            .user("Summarize this file concisely:\n\n" + content)
            .call()
            .content();
    } catch (IOException e) {
        return "Error reading file: " + e.getMessage();
    }
}
```

**Step 2: Add the import at the top of `KnowledgeBotCommands.java`**
```java
import java.nio.file.Files;
```

**Step 3: Test**
```bash
./mvnw spring-boot:run -pl knowledge-bot-cli
summarize src/main/java/com/knowledgebot/ai/model/ModelRouterService.java
```

**Rules to follow:**
- Always return a `String` — Spring Shell prints whatever you return
- Use `modelRouterService.getClientForComplexity()` to let the router choose the best model
- Do not inject services directly into `KnowledgeBotCommands` — it already has all major services; use what is already there
- Handle exceptions and return user-friendly error messages

---

### 5.3 Adding a New REST Endpoint

Example: You want a `/api/summarize` endpoint.

**Step 1: Add to `ChatController.java`**

```java
@PostMapping("/api/summarize")
public ResponseEntity<Map<String, String>> summarize(@RequestBody Map<String, String> request) {
    String content = request.get("content");
    if (content == null || content.isBlank()) {
        return ResponseEntity.badRequest().body(Map.of("error", "content is required"));
    }

    ChatClient client = modelRouterService.getClientForComplexity(TaskComplexity.SIMPLE);
    String summary = client.prompt()
        .user("Summarize this concisely:\n\n" + content)
        .call()
        .content();

    return ResponseEntity.ok(Map.of("summary", summary));
}
```

**Step 2: Test with curl**
```bash
curl -X POST http://localhost:8080/api/summarize \
  -H "Content-Type: application/json" \
  -d '{"content": "Put your text here"}'
```

---

### 5.4 Adding a New Agent Tool

An "agent tool" is a capability the AI can call as a function (function calling / tool use). Example: adding a tool that searches the web.

**Step 1: Create the tool class in `knowledge-bot-ai/.../agent/`**

```java
package com.knowledgebot.ai.agent;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class WebSearchTool {

    @Tool(description = "Search the web for current information about a topic")
    public String search(String query) {
        // Implement using a search API (SerpAPI, Bing, etc.)
        // For now, return a placeholder:
        return "Web search results for: " + query;
    }
}
```

**Step 2: Register the tool with the `ChatClient`**

In `ChatClientFactory.buildClient()`, add:
```java
.defaultTools(webSearchTool)
```

Spring AI's `@Tool` annotation automatically converts the method into a tool definition that the AI model can call.

**Step 3: Register with MCP (optional)**

If you want this tool available to external MCP clients, add it to `McpToolRegistry`.

---

### 5.5 Safely Removing a Feature

Before removing any class, follow this checklist:

1. **Find all usages:**
   ```bash
   grep -r "ClassName\|methodName" --include="*.java" .
   ```

2. **Check if it is a Spring bean:** If the class is annotated with `@Service`, `@Component`, or `@Repository`, other classes may depend on it via `@Autowired` or constructor injection. Removing it will cause Spring to fail to start.

3. **Remove in the correct order:**
   - Remove the class file
   - Remove all `import` statements referencing it
   - Remove constructor parameters in classes that depended on it
   - Remove calls to the removed class's methods
   - Remove `application.yml` configuration entries for it

4. **Compile immediately:**
   ```bash
   ./mvnw compile
   ```
   Do not remove multiple classes without compiling between each removal. Compilation errors cascade and become hard to track.

5. **Do not comment out code.** Remove it entirely. Commented-out code is clutter that confuses future developers.

**Example: Removing `NotificationService` (you don't use Discord/Telegram)**

```bash
# Step 1: Find what uses it
grep -r "NotificationService\|notificationService" --include="*.java" .
# Output: OrchestratorService.java, KnowledgeBotCommands.java

# Step 2: Remove from OrchestratorService.java
# - Delete the field: private final NotificationService notificationService;
# - Delete the constructor parameter and assignment
# - Delete calls: notificationService.notifyPlanGenerated(), notifyTaskComplete()

# Step 3: Remove from KnowledgeBotCommands.java similarly

# Step 4: Delete the file
rm knowledge-bot-ai/src/main/java/com/knowledgebot/ai/notifications/NotificationService.java
rm knowledge-bot-ai/src/main/java/com/knowledgebot/ai/notifications/NotificationEvent.java
rm knowledge-bot-ai/src/main/java/com/knowledgebot/ai/notifications/NotificationChannel.java

# Step 5: Compile
./mvnw compile
```

---

### 5.6 Updating Spring AI Version

Spring AI evolves rapidly. When a new version is released:

**Step 1: Check the Spring AI changelog**
Look at https://github.com/spring-projects/spring-ai/releases for breaking changes before upgrading.

**Step 2: Update the version in the root `pom.xml`**
```xml
<spring-ai.version>1.2.0</spring-ai.version>  <!-- change this -->
```

**Step 3: Check for common breaking changes:**

| Spring AI Change | What to look for |
|---|---|
| Starter artifact renamed | `NoSuchBeanDefinition` for `OllamaChatModel` or `OpenAiChatModel` |
| `ChatClient` API changed | Compilation errors in `ChatClientFactory` |
| `Document` constructor changed | `Document(content, metadata)` signature |
| `CallAdvisor` → `ChatModelObservationConvention` | `MetricsAdvisor` won't compile |
| `SearchRequest.builder()` → new API | `HybridSearchService` won't compile |
| `VectorStore.add()` signature | `EmbeddingPipeline` / `IndexingPipeline` |

**Step 4: Always compile and run tests after a version bump:**
```bash
./mvnw compile
./mvnw test
./mvnw spring-boot:run -pl knowledge-bot-cli
```

---

## 6. Best Practices & Troubleshooting

### Spring AI 1.1.0 Specific Rules

These rules exist because Spring AI 1.1.0 was a major breaking change release. If you see errors related to these patterns, these are the fixes:

**Rule 1: Never construct `OllamaApi` or `OpenAiApi` manually.**
Spring AI 1.1.0 removed these constructors. Always inject `OllamaChatModel` or `OpenAiChatModel` beans:
```java
// WRONG (pre-1.1.0):
OllamaApi api = new OllamaApi("http://localhost:11434");

// CORRECT (1.1.0+):
@Autowired OllamaChatModel ollamaChatModel;
```

**Rule 2: Use `Document(content, metadata)` not 3-arg constructor.**
```java
// WRONG: new Document(id, content, metadata) — removed
// CORRECT:
new Document(content, Map.of("source", (Object) "myfile.java", "id", id))
```

Note the `(Object)` cast. Java's `Map.of()` infers type `Map<String, String>` when all values are strings. Spring AI's `Document` expects `Map<String, Object>`. The explicit cast forces the right type.

**Rule 3: Use `document.getText()` not `document.getContent()`.**
The method was renamed in 1.1.0.

**Rule 4: Don't use `.variable()` for template injection.**
Spring AI 1.1.0 removed the `.variable("key", "value")` method from the prompt builder. Use string replacement instead:
```java
// WRONG:
client.prompt().user(u -> u.text("{intent}").variable("intent", userIntent))

// CORRECT:
String promptText = "Do this task: {intent}".replace("{intent}", userIntent);
client.prompt().user(promptText)
```

**Rule 5: `OllamaOptions` is now `OllamaChatOptions`.**
```java
// WRONG:
import org.springframework.ai.ollama.OllamaOptions;

// CORRECT:
import org.springframework.ai.ollama.api.OllamaChatOptions;
```

### Java 21 Rules

**Rule 1: Always verify Java version before building.**
```bash
java -version  # must show openjdk 21.x.x
```

**Rule 2: If you see `java.lang.UnsupportedClassVersionError`**, it means a class was compiled with a newer Java version than what is running it. Ensure `JAVA_HOME` points to Java 21 and nothing on your PATH points to a different Java.

**Rule 3: Virtual Threads require Java 19+.** Since this project uses `spring.threads.virtual.enabled: true`, you cannot run it on Java 17. Java 21 is the minimum.

### General Development Rules

**Always run `./mvnw compile` before assuming code is correct.** The previous AI agent introduced bugs (duplicated class bodies, incorrect API calls) that only became visible at compile time. Get in the habit of compiling after every significant change.

**Do not mix concerns across module boundaries.** If you are writing code in `knowledge-bot-core`, do not import from `knowledge-bot-ai`. Core is a dependency of AI, not the other way around.

**Test with the CLI before the web server.** The CLI starts faster (no web container) and the shell gives you immediate feedback. Use `./mvnw spring-boot:run -pl knowledge-bot-cli` during development.

**Check the logs for routing decisions.** Set `com.knowledgebot.ai.model: DEBUG` in `application.yml` to see which model is selected for each request and why. This is invaluable for understanding routing behavior.

### Troubleshooting Common Problems

**Problem: `No qualifying bean of type 'OllamaChatModel'`**
Cause: Spring AI auto-configuration didn't run, likely because the starter is on the classpath but the URL is wrong or Ollama isn't running.
Fix:
1. Verify Ollama is running: `curl http://localhost:11434/api/tags`
2. Check the starter artifact ID in `knowledge-bot-ai/pom.xml`: must be `spring-ai-starter-model-ollama`

**Problem: `VectorStore bean not found`**
Cause: pgvector starter missing or PostgreSQL not running.
Fix:
1. Verify PostgreSQL: `docker ps | grep postgres`
2. Check `knowledge-bot-data/pom.xml` for `spring-ai-starter-vector-store-pgvector`

**Problem: `Connection refused` to port 5432**
Cause: Docker containers not running.
Fix: `docker-compose up -d`

**Problem: Build fails with `package org.springframework.ai.vectorstore does not exist`**
Cause: `spring-ai-vector-store` dependency missing from a module's `pom.xml`.
Fix: Add `<dependency>...<artifactId>spring-ai-vector-store</artifactId><version>1.1.0</version>...` to the failing module's `pom.xml`.

**Problem: Dimensions mismatch error from pgvector**
Cause: The embedding model changed, but the database table still has columns sized for the old dimension count.
Fix: Connect to PostgreSQL and run:
```sql
DROP TABLE public.global_knowledge_vectors;
```
Then restart the application (it recreates the table with `initialize-schema: true`). **Warning: this deletes all indexed data.** Re-index your workspace after.

**Problem: Model takes 60+ seconds to respond**
Cause: Model weights not cached in Ollama, or system is running on CPU only (no GPU).
Fix: Pull the model first (`ollama pull deepseek-coder-v2:16b`) and wait for it to fully download. First response after a restart is always slow because the model loads into memory. Subsequent calls are faster.

---

## 7. Zero-Assumptions Local Setup Guide

This section assumes you have a fresh Windows laptop with nothing installed except a browser.

### Prerequisites

Install these tools in order:

#### 1. Java Development Kit 21

1. Go to https://adoptium.net
2. Choose **Temurin 21** (LTS), Windows, x64
3. Download the `.msi` installer and run it
4. Select "Add to PATH" and "Set JAVA_HOME" during installation
5. Verify: open a new terminal and run:
   ```
   java -version
   javac -version
   ```
   Both should show `21.x.x`.

#### 2. Git

1. Download Git from https://git-scm.com/download/win
2. Run the installer with default settings (select "Git from the command line and also from 3rd-party software" when asked)
3. Verify: `git --version`

#### 3. Docker Desktop

Docker Desktop runs the Ollama and PostgreSQL containers.

1. Download from https://www.docker.com/products/docker-desktop
2. Run the installer
3. After installation, open Docker Desktop and wait for the whale icon to show "Running" in the system tray
4. Verify: open a terminal and run `docker --version`

#### 4. IntelliJ IDEA (Backend IDE)

1. Download IntelliJ IDEA Community Edition (free) from https://www.jetbrains.com/idea/download
2. Run the installer with default settings

#### 5. VS Code (Optional: for editing YAML/markdown files)

1. Download from https://code.visualstudio.com
2. Run the installer with default settings
3. Recommended extensions: YAML, Java Extension Pack, Spring Boot Extension Pack

---

### Repository Setup

**Step 1: Clone the repository**
```bash
git clone <your-repository-url> AI-assistant-knowledgeBot
cd AI-assistant-knowledgeBot
```

**Step 2: Verify Java version in the project**

Open `pom.xml` (root) and confirm:
```xml
<java.version>21</java.version>
```
If it says `25` or any other version, change it to `21` and save.

---

### Docker Setup

**Step 1: Start Docker Desktop**
Wait until the system tray icon shows it is running.

**Step 2: Start the services**
```bash
cd AI-assistant-knowledgeBot
docker-compose up -d
```

You should see:
```
Creating network "ai-assistant-knowledgebot_default" with the default driver
Creating knowledge-bot-ollama   ... done
Creating knowledge-bot-postgres ... done
```

**Step 3: Verify services are running**
```bash
docker ps
```
You should see two containers: `knowledge-bot-ollama` and `knowledge-bot-postgres`.

**Step 4: Pull AI models into Ollama**

This downloads the model weights (~2-16 GB each, one-time download):
```bash
# The fast local model (small, ~2 GB)
docker exec knowledge-bot-ollama ollama pull llama3.2:3b

# The code model (larger, ~10 GB)
docker exec knowledge-bot-ollama ollama pull deepseek-coder-v2:16b

# The embedding model (small, ~274 MB)
docker exec knowledge-bot-ollama ollama pull nomic-embed-text
```

Wait for all three to finish. The terminal shows download progress.

**Step 5: Verify models are available**
```bash
curl http://localhost:11434/api/tags
```
You should see JSON listing all three models.

---

### Backend Setup in IntelliJ IDEA

**Step 1: Open the project**
1. Open IntelliJ IDEA
2. Click **File → Open**
3. Navigate to the `AI-assistant-knowledgeBot` folder
4. Click **Open**
5. When asked "Maven Project found", click **Open as Maven Project**

**Step 2: Configure the Java SDK**
1. Click **File → Project Structure** (or press Ctrl+Alt+Shift+S)
2. Under **Project**, set **Project SDK** to **21 (Temurin)**
3. If 21 is not listed: click the dropdown → **Add SDK → JDK** → navigate to your JDK 21 installation folder
4. Set **Project Language Level** to **21**
5. Click **Apply** and **OK**

**Step 3: Reload Maven**
1. In the right sidebar, click the **Maven** tab
2. Click the **Reload All Maven Projects** button (circular arrow icon)
3. Wait for IntelliJ to download all dependencies (this can take 5-10 minutes on first run as it downloads ~200 MB of JAR files)

**Step 4: Set environment variables**

Some features require API keys set as environment variables:

1. Click **Run → Edit Configurations**
2. Click **+** and choose **Spring Boot**
3. Name it something like `knowledge-bot-web`
4. Set **Main class** to `com.knowledgebot.KnowledgeBotApplication`
5. Set **Module** to `knowledge-bot-web`
6. Click **Modify options → Environment variables**
7. Add these (only if you need cloud model routing):
   ```
   OPENAI_API_KEY=sk-your-key-here
   ```
8. Click **OK**

**Step 5: Verify the build**

Open a terminal in IntelliJ (View → Tool Windows → Terminal) and run:
```bash
./mvnw compile
```

Expected output (all 8 modules, `BUILD SUCCESS`):
```
[INFO] knowledge-bot-parent ....... SUCCESS
[INFO] knowledge-bot-common ....... SUCCESS
[INFO] knowledge-bot-core ......... SUCCESS
[INFO] knowledge-bot-ai ........... SUCCESS
[INFO] knowledge-bot-data ......... SUCCESS
[INFO] knowledge-bot-web .......... SUCCESS
[INFO] knowledge-bot-cli .......... SUCCESS
[INFO] knowledge-bot-mcp .......... SUCCESS
[INFO] BUILD SUCCESS
```

If you see any `ERROR`, refer to section 9 (Gotchas).

**Step 6: Run the CLI**
```bash
./mvnw spring-boot:run -pl knowledge-bot-cli
```

You should see the Spring Boot banner, then a `shell:>` prompt. Try:
```
shell:> ask "What is this project?"
shell:> status
```

**Step 7: Run the Web API (optional)**
```bash
./mvnw spring-boot:run -pl knowledge-bot-web
```

Test it:
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Hello, what can you do?"}'
```

---

### Frontend Setup

**There is currently no frontend in this project.** The `knowledge-bot-web` module exposes a REST API only. A future Angular or React frontend would be a separate project that calls this API.

If and when a frontend is added, the typical setup would be:
1. Install Node.js 20+ from https://nodejs.org
2. Install the framework CLI (`npm install -g @angular/cli` for Angular)
3. Run `npm install` in the frontend directory
4. Run `npm start` (typically starts on port 4200)
5. Configure CORS in `ChatController.java`: add `@CrossOrigin(origins = "http://localhost:4200")`

---

### Verifying Everything Works End-to-End

Run this checklist in order:

```bash
# 1. Docker services running
docker ps | grep "knowledge-bot"
# ✓ Should show two rows

# 2. Ollama responding
curl http://localhost:11434/api/tags | grep "llama3"
# ✓ Should show llama3.2:3b in the list

# 3. PostgreSQL accepting connections
docker exec knowledge-bot-postgres pg_isready -U kbot
# ✓ Should show: /var/run/postgresql:5432 - accepting connections

# 4. Project compiles
./mvnw compile
# ✓ Should show: BUILD SUCCESS

# 5. CLI starts and responds
./mvnw spring-boot:run -pl knowledge-bot-cli
# ✓ Should show: shell:> prompt
# Type: status
# ✓ Should show model routing metrics table
```

---

## 8. Environment Variables Reference

These are all environment variables the application reads. None are required to start the application (defaults are used), but some features won't work without them.

| Variable | Module | Default | Purpose |
|---|---|---|---|
| `OPENAI_API_KEY` | knowledge-bot-web | `your-api-key-here` | Enables `gpt-4o` cloud routing. Without this, cloud-reasoning and cloud-creative models are configured but will fail if selected. |
| `DISCORD_WEBHOOK_URL` | knowledge-bot-web | _(empty)_ | Enables Discord notifications when tasks complete. |
| `TELEGRAM_BOT_TOKEN` | knowledge-bot-web | _(empty)_ | Enables Telegram notifications. Requires `TELEGRAM_CHAT_ID` too. |
| `TELEGRAM_CHAT_ID` | knowledge-bot-web | _(empty)_ | Telegram chat to send notifications to. |
| `GENERIC_WEBHOOK_URL` | knowledge-bot-web | _(empty)_ | Any HTTP endpoint to receive task completion events. |

### How to Set Environment Variables

**For IntelliJ Run Configurations:** Edit Configurations → Environment variables field.

**For terminal (Windows, session-scoped):**
```cmd
set OPENAI_API_KEY=sk-...
./mvnw spring-boot:run -pl knowledge-bot-web
```

**For terminal (Windows, persistent):**
```cmd
setx OPENAI_API_KEY "sk-..."
```
Then open a new terminal for the change to take effect.

**For `.env` file (if you add dotenv support):** Currently not supported. You would need to add the `dotenv-java` library to enable this.

---

## 9. Gotchas & Common Setup Errors

### Java Version Gotchas

**Gotcha: Wrong Java version causes cryptic errors**

Symptom: `./mvnw compile` fails with `invalid source release: 21` or `class file version 65.0`.

Cause: Your `JAVA_HOME` points to a different Java version than the project's `pom.xml` specifies.

Fix:
```bash
# Check what Java Maven is using
./mvnw -version

# Should say: Java version: 21.x.x

# If not, set JAVA_HOME explicitly:
# Windows:
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.x.x-hotspot
./mvnw compile
```

**Gotcha: Two Java versions installed, wrong one on PATH**

Symptom: `java -version` shows 21 but Maven uses a different version.

Fix: In `pom.xml`, you can force the Java version:
```xml
<properties>
    <java.version>21</java.version>
    <maven.compiler.source>21</maven.compiler.source>
    <maven.compiler.target>21</maven.compiler.target>
</properties>
```

---

### Docker Gotchas

**Gotcha: Port 5432 already in use**

Symptom: `docker-compose up -d` shows `Bind for 0.0.0.0:5432 failed: port is already allocated`.

Cause: You have a local PostgreSQL installation also running on port 5432.

Fix options:
1. Stop your local PostgreSQL: `net stop postgresql-x64-17` (Windows)
2. Or change the Docker mapping in `docker-compose.yml`:
   ```yaml
   ports:
     - "5433:5432"   # Maps Docker's 5432 to host's 5433
   ```
   Then update `application.yml`:
   ```yaml
   datasource:
     url: jdbc:postgresql://localhost:5433/knowledgebot
   ```

**Gotcha: Ollama container has no GPU access**

Symptom: Model responses take 60+ seconds even for simple queries.

Cause: Ollama is running on CPU only. GPU acceleration requires the NVIDIA Container Toolkit.

Fix (if you have an NVIDIA GPU):
1. Install NVIDIA Container Toolkit: https://docs.nvidia.com/datacenter/cloud-native/container-toolkit/install-guide.html
2. Uncomment the `deploy` block in `docker-compose.yml`:
   ```yaml
   deploy:
     resources:
       reservations:
         devices:
           - driver: nvidia
             count: 1
             capabilities: [gpu]
   ```
3. Restart: `docker-compose down && docker-compose up -d`

**Gotcha: Models disappear after `docker-compose down`**

Symptom: After running `docker-compose down` and `docker-compose up -d`, Ollama no longer has the models.

Cause: `docker-compose down` removes containers but **not volumes** by default. However, if you used `docker-compose down -v` (removes volumes), the `ollama_data` volume is deleted.

Fix: Re-pull the models:
```bash
docker exec knowledge-bot-ollama ollama pull llama3.2:3b
docker exec knowledge-bot-ollama ollama pull deepseek-coder-v2:16b
docker exec knowledge-bot-ollama ollama pull nomic-embed-text
```

---

### Spring AI Gotchas

**Gotcha: `NoSuchBeanDefinitionException: OllamaChatModel`**

Cause: `spring-ai-starter-model-ollama` is not on the classpath, or Ollama isn't running and the health check fails.

Fix:
1. Verify `knowledge-bot-ai/pom.xml` has:
   ```xml
   <artifactId>spring-ai-starter-model-ollama</artifactId>
   ```
2. Verify Ollama is running: `curl http://localhost:11434/api/tags`

**Gotcha: pgvector `ERROR: type "vector" does not exist`**

Cause: The pgvector extension is not enabled in the database. Should be automatic with the `pgvector/pgvector:pg17` image, but can fail if the container was created before the extension was packaged.

Fix:
```bash
docker exec -it knowledge-bot-postgres psql -U kbot -d knowledgebot
# In psql:
CREATE EXTENSION IF NOT EXISTS vector;
\q
```

**Gotcha: `spring-ai-vector-store` not found in Maven repository**

Cause: This artifact is published to the Spring Milestones repository, not Maven Central.

Fix: Verify the root `pom.xml` has the Spring Milestones repository:
```xml
<repositories>
    <repository>
        <id>spring-milestones</id>
        <name>Spring Milestones</name>
        <url>https://repo.spring.io/milestone</url>
    </repository>
</repositories>
```

---

### General Application Gotchas

**Gotcha: `index` command does nothing / vector search returns empty results**

Cause: The workspace has not been indexed yet.

Fix:
```bash
# In the CLI shell:
index /path/to/your/project
```
Wait for "Successfully indexed X documents" in the log. Then try `ask` again.

**Gotcha: CORS errors when calling REST API from a frontend**

Cause: Browsers block cross-origin requests by default. The web module needs to explicitly allow requests from the frontend's origin.

Fix: Add to `ChatController.java`:
```java
@CrossOrigin(origins = "http://localhost:4200")   // or whatever port your frontend uses
@RestController
public class ChatController { ... }
```

**Gotcha: `plan-status` shows "No active plan"**

Cause: No plan has been started in the current session. The `activePlan` in `OrchestratorService` is an in-memory reference — it resets when the application restarts.

Fix: Run a `plan` command first to start a plan, then use `plan-status` to check its progress.

**Gotcha: The bot gives wrong or outdated answers about the codebase**

Cause: The vector store has stale data from a previous indexing run that predates your recent code changes.

Fix: Re-index the workspace:
```bash
# In the CLI:
index /path/to/your/project
```
Or watch the directory for automatic re-indexing:
```bash
watch /path/to/your/project
```

---

## Quick Reference Card

```
Start services:    docker-compose up -d
Pull models:       docker exec knowledge-bot-ollama ollama pull llama3.2:3b
Run CLI:           ./mvnw spring-boot:run -pl knowledge-bot-cli
Run Web API:       ./mvnw spring-boot:run -pl knowledge-bot-web
Compile only:      ./mvnw compile
Run tests:         ./mvnw test

CLI Commands:
  ask "<q>"          Ask a question about the codebase
  index <path>       Index a directory
  watch <path>       Watch and auto-reindex
  generate "<task>"  Generate code
  plan "<goal>"      Execute multi-step plan
  plan-status        Show active plan progress
  status             Show model routing metrics
  semantic-analyze   Show Java AST analysis

Key config file:   knowledge-bot-web/src/main/resources/application.yml
Docker services:   docker-compose.yml
Root POM:          pom.xml
```

---

*This guide was written based on the project state as of 2026-04-02, with Spring AI 1.1.0 and Spring Boot 4.0.0. When upgrading dependencies, always re-verify the Spring AI API sections of this guide, as that library has a history of breaking changes between versions.*
