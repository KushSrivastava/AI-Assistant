package com.knowledgebot.ai.architect;

import com.knowledgebot.ai.agent.AgentLoopService;
import com.knowledgebot.ai.model.WorkspaceManager;
import com.knowledgebot.ai.tools.FileWriteTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * ARCHITECT MODE — HLD → LLD → Code
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * WHY: Jumping straight to writing code for a complex feature is how you
 * end up with spaghetti architecture. A senior engineer thinks first:
 *   1. WHAT is the system? (HLD)
 *   2. HOW are the pieces designed? (LLD)
 *   3. Implement it exactly per the design.
 *
 * This service enforces that three-phase discipline on the AI agent.
 *
 * PHASE 1 — HLD (High-Level Design):
 *   Produces HLD.md — component overview, data flow, tech stack, API contracts.
 *
 * PHASE 2 — LLD (Low-Level Design):
 *   Reads the HLD and produces LLD.md — package structure, class diagrams
 *   (fields + signatures), DB DDL, dependencies.
 *
 * PHASE 3 — Implementation:
 *   The existing AgentLoopService (with all 11 tools) implements the LLD
 *   file-by-file, running the build after each file and fixing errors.
 *
 * OUTPUT:
 *   - docs/HLD.md  — saved to workspace
 *   - docs/LLD.md  — saved to workspace
 *   - All source files created in-workspace by the agent
 *   - Returns ArchitectResult record with all three artifacts
 */
@Service
public class ArchitectService {

    private static final Logger log = LoggerFactory.getLogger(ArchitectService.class);

    private final AgentLoopService agentLoopService;
    private final FileWriteTool fileWriteTool;
    private final WorkspaceManager workspaceManager;

    private static final String HLD_PROMPT_TEMPLATE = """
        You are a Principal Software Architect with 15 years of experience.
        Create a High-Level Design (HLD) document for the following goal.
        
        GOAL: %s
        
        Your HLD MUST include these exact sections:
        
        ## 1. System Overview
        One paragraph describing what the system does, who uses it, and the key challenges.
        
        ## 2. Architecture Style
        Choose one: Monolith / Microservices / Modular Monolith / Event-Driven.
        Justify your choice for this specific use case.
        
        ## 3. Component Architecture
        List each component with:
        - Component name
        - Responsibility (one sentence)
        - Dependencies (what it calls)
        
        ## 4. Technology Stack
        | Layer | Technology | Why |
        For each choice, explain why it's the best fit (not just familiarity).
        
        ## 5. Data Flow
        Describe the flow of data for the primary use case using a numbered step-by-step.
        Example: 1. User submits form → 2. API validates → 3. Service processes → 4. DB stores
        
        ## 6. API Contracts
        List the key REST endpoints or interfaces:
        - Method + Path, request body, response body, error codes
        
        ## 7. Database Schema
        List the main entities with their key fields (no need for all columns, just the design).
        
        ## 8. Security Considerations
        Authentication, authorization, data protection concerns.
        
        ## 9. Scalability & Risks
        Known risks and how to mitigate them.
        
        Format as clean Markdown. Be specific and opinionated — not generic.
        """;

    private static final String LLD_PROMPT_TEMPLATE = """
        You are a Senior Software Engineer. Based on the following HLD, create a
        Low-Level Design (LLD) document. Be extremely precise — this LLD will be
        used by the agent to implement the code without further clarification.
        
        HIGH-LEVEL DESIGN:
        ---
        %s
        ---
        
        Your LLD MUST include:
        
        ## 1. Package Structure
        Show the FULL package tree (like a file tree with all packages and classes).
        Example:
        ```
        src/main/java/com/example/
        ├── config/
        │   └── SecurityConfig.java
        ├── controller/
        │   └── UserController.java
        ├── service/
        │   ├── UserService.java
        │   └── impl/UserServiceImpl.java
        └── repository/
            └── UserRepository.java
        ```
        
        ## 2. Class Designs
        For EVERY class listed in the package structure, provide:
        ```java
        // Package: com.example.controller
        // Annotations: @RestController, @RequestMapping("/api/v1/users")
        public class UserController {
            // Fields with types
            private final UserService userService;
            
            // Method signatures (no body needed)
            public ResponseEntity<UserDto> createUser(@RequestBody CreateUserRequest req) { ... }
            public ResponseEntity<List<UserDto>> getAllUsers() { ... }
        }
        ```
        
        ## 3. Data Transfer Objects (DTOs)
        Define all request/response DTOs as Java records or classes with fields.
        
        ## 4. Database Schema (DDL)
        ```sql
        CREATE TABLE users (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            ...
        );
        ```
        
        ## 5. pom.xml Dependencies
        List all needed Maven dependencies with groupId, artifactId, version.
        
        ## 6. application.yml Configuration
        Show all needed config properties.
        
        ## 7. Implementation Order
        List the exact order to create files (dependencies first):
        1. Create pom.xml / update dependencies
        2. Create entity classes
        3. Create repository interfaces
        4. Create service interfaces + implementations
        5. Create controllers
        6. Create DTOs
        7. Run build
        
        Be SPECIFIC. Every class name, every package, every field. No vague descriptions.
        """;

    private static final String IMPL_PROMPT_TEMPLATE = """
        You are an expert Java developer. Implement the following Low-Level Design COMPLETELY.
        
        IMPLEMENTATION RULES (follow exactly):
        1. Start with listDirectory('.') to understand the existing project layout.
        2. Read pom.xml to understand existing dependencies before adding new ones.
        3. Create files in the EXACT ORDER specified in the LLD's "Implementation Order" section.
        4. After EVERY file you create or modify, run 'mvn compile -q'.
        5. If compile fails: read the error, fix the file, recompile. Do NOT move on until green.
        6. Use the exact class names, package names, and field types from the LLD.
        7. Write COMPLETE code — no TODO comments, no stubs.
        8. After all files are created, run 'mvn test' and fix any test failures.
        
        LOW-LEVEL DESIGN:
        ---
        %s
        ---
        
        Begin now. Document each step you take.
        """;

    public ArchitectService(AgentLoopService agentLoopService,
                            FileWriteTool fileWriteTool,
                            WorkspaceManager workspaceManager) {
        this.agentLoopService = agentLoopService;
        this.fileWriteTool = fileWriteTool;
        this.workspaceManager = workspaceManager;
    }

    /**
     * Full architect pipeline: HLD → LLD → Implementation.
     *
     * This is a LONG-RUNNING operation — each phase can take minutes.
     * Use the streaming endpoint for the UI to show real-time progress.
     *
     * @param goal User's natural language goal, e.g.:
     *             "Build a REST API for a library management system with
     *              user auth, book catalog, and loan tracking"
     * @return ArchitectResult with all three documents
     */
    public ArchitectResult architect(String goal) {
        log.info("═══ ARCHITECT MODE START ═══");
        log.info("Goal: {}", goal);

        if (!workspaceManager.isWorkspaceAttached()) {
            throw new IllegalStateException(
                "No workspace attached. Attach a workspace before running Architect Mode.");
        }

        // ── PHASE 1: High-Level Design ─────────────────────────────────────
        log.info("▶ Phase 1: Generating HLD...");
        String hldPrompt = HLD_PROMPT_TEMPLATE.formatted(goal);
        String hld = agentLoopService.execute(hldPrompt, List.of()).content();

        // Save HLD to workspace docs/HLD.md (agent uses tools internally)
        fileWriteTool.writeFile("docs/HLD.md", hld);
        log.info("✅ HLD complete ({} chars) — saved to docs/HLD.md", hld.length());

        // ── PHASE 2: Low-Level Design ──────────────────────────────────────
        log.info("▶ Phase 2: Generating LLD from HLD...");
        String lldPrompt = LLD_PROMPT_TEMPLATE.formatted(hld);
        String lld = agentLoopService.execute(lldPrompt, List.of()).content();

        fileWriteTool.writeFile("docs/LLD.md", lld);
        log.info("✅ LLD complete ({} chars) — saved to docs/LLD.md", lld.length());

        // ── PHASE 3: Implementation ────────────────────────────────────────
        log.info("▶ Phase 3: Implementing from LLD (this will take the longest)...");
        String implPrompt = IMPL_PROMPT_TEMPLATE.formatted(lld);
        String implResult = agentLoopService.execute(implPrompt, List.of()).content();

        log.info("═══ ARCHITECT MODE COMPLETE ═══");
        return new ArchitectResult(goal, hld, lld, implResult);
    }

    /**
     * Generate only HLD (useful for quick design reviews before committing to implementation).
     */
    public String generateHld(String goal) {
        log.info("▶ Generating HLD only for: {}", goal);
        String hld = agentLoopService.execute(HLD_PROMPT_TEMPLATE.formatted(goal), List.of()).content();
        if (workspaceManager.isWorkspaceAttached()) {
            fileWriteTool.writeFile("docs/HLD.md", hld);
        }
        return hld;
    }

    /**
     * Generate only LLD from an existing HLD (useful for iterating on design).
     */
    public String generateLld(String hld) {
        log.info("▶ Generating LLD from provided HLD...");
        String lld = agentLoopService.execute(LLD_PROMPT_TEMPLATE.formatted(hld), List.of()).content();
        if (workspaceManager.isWorkspaceAttached()) {
            fileWriteTool.writeFile("docs/LLD.md", lld);
        }
        return lld;
    }

    /**
     * Result record containing all three architect artifacts.
     *
     * @param goal           The original user goal
     * @param hld            Full HLD.md content
     * @param lld            Full LLD.md content
     * @param implementation Agent's implementation report
     */
    public record ArchitectResult(
        String goal,
        String hld,
        String lld,
        String implementation
    ) {}
}
