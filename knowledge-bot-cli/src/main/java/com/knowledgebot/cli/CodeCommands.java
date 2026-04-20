package com.knowledgebot.cli;

import com.knowledgebot.ai.generation.CodeGenerationService;
import com.knowledgebot.ai.migration.MigrationGeneratorService;
import com.knowledgebot.ai.modernize.DiffGenerationService;
import com.knowledgebot.ai.model.ModeManager;
import com.knowledgebot.ai.model.WorkspaceManager;
import com.knowledgebot.core.security.CommandPermissionService;
import com.knowledgebot.core.security.OutputSanitizer;
import com.knowledgebot.core.security.PromptInjectionGuard;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Handles code generation, modernization, and migration commands.
 * Extracted from the monolithic KnowledgeBotCommands.
 */
@ShellComponent
public class CodeCommands {

    private final CodeGenerationService codeGenService;
    private final DiffGenerationService legacyDiffService;
    private final MigrationGeneratorService migrationService;
    private final ModeManager modeManager;
    private final WorkspaceManager workspaceManager;
    private final CommandPermissionService permissionService;
    private final OutputSanitizer sanitizer;
    private final PromptInjectionGuard injectionGuard;

    public CodeCommands(CodeGenerationService codeGenService,
                        DiffGenerationService legacyDiffService,
                        MigrationGeneratorService migrationService,
                        ModeManager modeManager,
                        WorkspaceManager workspaceManager,
                        CommandPermissionService permissionService,
                        OutputSanitizer sanitizer,
                        PromptInjectionGuard injectionGuard) {
        this.codeGenService = codeGenService;
        this.legacyDiffService = legacyDiffService;
        this.migrationService = migrationService;
        this.modeManager = modeManager;
        this.workspaceManager = workspaceManager;
        this.permissionService = permissionService;
        this.sanitizer = sanitizer;
        this.injectionGuard = injectionGuard;
    }

    @ShellMethod(key = "generate", value = "Generate code based on instructions")
    public String generate(@ShellOption String prompt, @ShellOption(defaultValue = "") String fileTarget) {
        if (!modeManager.canModifyFiles()) {
            return "[MODE BLOCK] Switch to CODE mode to generate code. Use: set-mode CODE";
        }
        if (!permissionService.requestPermission("Generate code based on prompt: " + prompt)) {
            return "Action Denied by user.";
        }
        if (!injectionGuard.isSafe(prompt)) return "[SECURITY BLOCK] Query rejected.";
        Path targetPath = fileTarget.isEmpty() ? null : workspaceManager.resolve(fileTarget);
        return sanitizer.sanitize(codeGenService.generate(prompt, targetPath));
    }

    @ShellMethod(key = "modernize", value = "Modernize a legacy Java file to Java 21+ idioms")
    public String modernize(@ShellOption String fileTarget) {
        if (!modeManager.canModifyFiles()) {
            return "[MODE BLOCK] Switch to CODE mode to modernize files. Use: set-mode CODE";
        }
        if (!permissionService.requestPermission("Analyze and modernize file: " + fileTarget)) {
            return "Action Denied by user.";
        }
        try {
            Path targetPath = workspaceManager.resolve(fileTarget);
            return sanitizer.sanitize(legacyDiffService.modernize(targetPath));
        } catch (IOException e) {
            return "Failed to modernize: " + e.getMessage();
        }
    }

    @ShellMethod(key = "migrate-gen", value = "Generate Flyway Migration SQL out of JPA Entities")
    public String migrateGen(@ShellOption String fileTarget) {
        if (!modeManager.canModifyFiles()) {
            return "[MODE BLOCK] Switch to CODE mode to generate migrations. Use: set-mode CODE";
        }
        if (!permissionService.requestPermission("Read/Analyze file to generate database migration: " + fileTarget)) {
            return "Action Denied by user.";
        }
        Path targetPath = workspaceManager.resolve(fileTarget);
        return sanitizer.sanitize(migrationService.generateMigration(targetPath));
    }
}
