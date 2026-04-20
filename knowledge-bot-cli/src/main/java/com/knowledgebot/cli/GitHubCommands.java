package com.knowledgebot.cli;

import com.knowledgebot.ai.devops.GitHubApiService;
import com.knowledgebot.ai.model.ModeManager;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

/**
 * Handles GitHub API commands: PR creation, CI status, token management.
 * Extracted from the monolithic KnowledgeBotCommands.
 */
@ShellComponent
public class GitHubCommands {

    private final GitHubApiService githubApiService;
    private final ModeManager modeManager;

    public GitHubCommands(GitHubApiService githubApiService, ModeManager modeManager) {
        this.githubApiService = githubApiService;
        this.modeManager = modeManager;
    }

    @ShellMethod(key = "github-pr", value = "Create a GitHub Pull Request")
    public String githubPr(@ShellOption String owner,
                           @ShellOption String repo,
                           @ShellOption String title,
                           @ShellOption String body,
                           @ShellOption String headBranch,
                           @ShellOption(defaultValue = "main") String baseBranch) {
        if (!modeManager.canModifyFiles()) {
            return "[MODE BLOCK] Switch to CODE mode for GitHub operations. Use: set-mode CODE";
        }
        return githubApiService.createPullRequest(owner, repo, title, body, headBranch, baseBranch);
    }

    @ShellMethod(key = "github-ci-status", value = "Check CI/CD status for a repository")
    public String githubCiStatus(@ShellOption String owner,
                                 @ShellOption String repo,
                                 @ShellOption(defaultValue = "main") String branch) {
        return githubApiService.getCIStatus(owner, repo, branch);
    }

    @ShellMethod(key = "github-token", value = "Set GitHub API authentication token")
    public String githubToken(@ShellOption String token) {
        githubApiService.setAuthToken(token);
        return "GitHub API token set successfully.";
    }
}
