package com.knowledgebot.ai.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicReference;

@Service
public class ModeManager {

    private static final Logger log = LoggerFactory.getLogger(ModeManager.class);

    private final WorkspaceManager workspaceManager;
    private final AtomicReference<BotMode> currentMode = new AtomicReference<>(BotMode.ASK);

    public ModeManager(WorkspaceManager workspaceManager) {
        this.workspaceManager = workspaceManager;
    }

    public BotMode getCurrentMode() {
        return currentMode.get();
    }

    public void setMode(BotMode mode) {
        if ((mode == BotMode.CODE || mode == BotMode.PLAN) && !workspaceManager.isWorkspaceAttached()) {
            throw new IllegalStateException("Cannot switch to " + mode + " mode without an attached workspace. Use: attach-workspace <path>");
        }
        BotMode previous = currentMode.getAndSet(mode);
        log.info("Mode switched: {} -> {}", previous, mode);
    }

    public boolean canModifyFiles() {
        return currentMode.get() == BotMode.CODE;
    }

    public boolean canCreateFiles() {
        return currentMode.get() == BotMode.CODE || currentMode.get() == BotMode.PLAN;
    }

    public boolean isReadOnly() {
        return currentMode.get() == BotMode.ASK;
    }

    public String getModeDescription() {
        return switch (currentMode.get()) {
            case PLAN -> "PLAN mode: Can only create documentation files (PLAN.md, specs, etc.)";
            case CODE -> "CODE mode: Full file access - create, modify, delete files and folders";
            case ASK -> "ASK mode: Read-only chat, no file modifications allowed";
        };
    }
}
