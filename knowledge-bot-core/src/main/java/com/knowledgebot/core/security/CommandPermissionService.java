package com.knowledgebot.core.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class CommandPermissionService {
    private static final Logger log = LoggerFactory.getLogger(CommandPermissionService.class);

    /**
     * WHY: Safe Mode with System.in blocking is only meaningful in the CLI module.
     * When running as a web server, there is no console user to answer prompts,
     * so blocking on Scanner.nextLine() causes the agent to hang indefinitely.
     *
     * DEFAULT: false — the web API auto-approves all agent actions.
     * The CLI module can call setSafeMode(true) to re-enable interactive prompts.
     */
    private final AtomicBoolean safeMode = new AtomicBoolean(false);
    private final ThreadLocal<String> userContext = ThreadLocal.withInitial(() -> "Anonymous");

    /**
     * When true, the agent will call the UI approval callback instead of
     * blocking on stdin. Set by the WebSocket permission handler (future).
     */
    private final AtomicBoolean webMode = new AtomicBoolean(true);

    public boolean isSafeMode() {
        return safeMode.get();
    }

    public void setSafeMode(boolean enabled) {
        this.safeMode.set(enabled);
        log.info("Safe Mode is now: {}", enabled ? "ENABLED (approval required)" : "DISABLED (autonomous)");
    }

    public void setWebMode(boolean enabled) {
        this.webMode.set(enabled);
        log.info("Web Mode is now: {}", enabled ? "ENABLED (auto-approve)" : "DISABLED (use stdin)");
    }

    public void setUserContext(String user) {
        userContext.set(user);
    }

    /**
     * Request permission for an action.
     *
     * In web mode (default): auto-approves and logs the action.
     * In CLI mode (webMode=false, safeMode=true): blocks on stdin for user input.
     * In autonomous mode (safeMode=false): always approves immediately.
     */
    public boolean requestPermission(String action) {
        if (!safeMode.get()) {
            log.info("Autonomous mode — auto-approving: {}", action);
            return true;
        }

        // Web server context: never block on stdin — auto-approve and log
        if (webMode.get()) {
            log.info("Web mode — auto-approving action: {}", action);
            return true;
        }

        // CLI context only: interactive stdin approval
        String currentUser = userContext.get();
        System.out.printf("\n[PERMISSION REQUIRED] User: %s | The AI wants to: %s\n", currentUser, action);
        System.out.print("Approve? (y/n): ");

        Scanner scanner = new Scanner(System.in);
        String input = scanner.nextLine().trim().toLowerCase();

        boolean approved = "y".equals(input) || "yes".equals(input);
        if (approved) {
            log.info("Action APPROVED by user: {}", action);
        } else {
            log.warn("Action DENIED by user: {}", action);
        }
        return approved;
    }
}
