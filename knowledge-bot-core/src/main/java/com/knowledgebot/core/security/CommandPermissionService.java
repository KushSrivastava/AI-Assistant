package com.knowledgebot.core.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class CommandPermissionService {
    private static final Logger log = LoggerFactory.getLogger(CommandPermissionService.class);
    private final AtomicBoolean safeMode = new AtomicBoolean(true);
    private final ThreadLocal<String> userContext = ThreadLocal.withInitial(() -> "Anonymous");

    public boolean isSafeMode() {
        return safeMode.get();
    }

    public void setSafeMode(boolean enabled) {
        this.safeMode.set(enabled);
        log.info("Safe Mode is now: {}", enabled ? "ENABLED (Human approval required)" : "DISABLED (Autonomous Mode)");
    }

    public void setUserContext(String user) {
        userContext.set(user);
    }

    public boolean requestPermission(String action) {
        if (!safeMode.get()) {
            log.info("Safe Mode DISABLED. Auto-permitting action: {}", action);
            return true;
        }

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
