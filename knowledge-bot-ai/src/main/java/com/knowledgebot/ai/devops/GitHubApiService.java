package com.knowledgebot.ai.devops;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class GitHubApiService {

    private static final Logger log = LoggerFactory.getLogger(GitHubApiService.class);
    private static final String GITHUB_API = "https://api.github.com";

    private final HttpClient httpClient;
    private String authToken;

    public GitHubApiService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public void setAuthToken(String token) {
        this.authToken = token;
    }

    public String createPullRequest(String owner, String repo, String title, String body, String headBranch, String baseBranch) {
        String jsonPayload = """
            {
                "title": "%s",
                "body": %s,
                "head": "%s",
                "base": "%s"
            }
            """.formatted(title, escapeJson(body), headBranch, baseBranch);

        return makeRequest("POST", "/repos/" + owner + "/" + repo + "/pulls", jsonPayload);
    }

    public String getPullRequest(String owner, String repo, int prNumber) {
        return makeRequest("GET", "/repos/" + owner + "/" + repo + "/pulls/" + prNumber, null);
    }

    public String listPullRequests(String owner, String repo, String state) {
        return makeRequest("GET", "/repos/" + owner + "/" + repo + "/pulls?state=" + state, null);
    }

    public String getWorkflowRuns(String owner, String repo, String branch) {
        return makeRequest("GET", "/repos/" + owner + "/" + repo + "/actions/runs?branch=" + branch, null);
    }

    public String getWorkflowRunLogs(String owner, String repo, long runId) {
        return makeRequest("GET", "/repos/" + owner + "/" + repo + "/actions/runs/" + runId + "/logs", null);
    }

    public String createIssue(String owner, String repo, String title, String body, List<String> labels) {
        String labelsJson = labels.isEmpty() ? "[]" : 
                labels.stream().map(l -> "\"" + l + "\"").reduce((a, b) -> a + "," + b).orElse("[]");
        String jsonPayload = """
            {
                "title": "%s",
                "body": %s,
                "labels": [%s]
            }
            """.formatted(title, escapeJson(body), labelsJson);

        return makeRequest("POST", "/repos/" + owner + "/" + repo + "/issues", jsonPayload);
    }

    public String getRepositoryInfo(String owner, String repo) {
        return makeRequest("GET", "/repos/" + owner + "/" + repo, null);
    }

    public String pushToBranch(String owner, String repo, String branch, String filePath, String content, String commitMessage) {
        String encodedContent = Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8));
        String jsonPayload = """
            {
                "message": "%s",
                "content": "%s",
                "branch": "%s"
            }
            """.formatted(commitMessage, encodedContent, branch);

        return makeRequest("PUT", "/repos/" + owner + "/" + repo + "/contents/" + filePath, jsonPayload);
    }

    public String getCIStatus(String owner, String repo, String branch) {
        String runsResponse = getWorkflowRuns(owner, repo, branch);
        return "CI/CD Status for " + owner + "/" + repo + " (branch: " + branch + "):\n" + runsResponse;
    }

    private String makeRequest(String method, String path, String body) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(GITHUB_API + path))
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "KnowledgeBot/1.0")
                    .timeout(Duration.ofSeconds(30));

            if (authToken != null && !authToken.isEmpty()) {
                builder.header("Authorization", "Bearer " + authToken);
            }

            HttpRequest request = switch (method) {
                case "GET" -> builder.GET().build();
                case "POST" -> builder.POST(HttpRequest.BodyPublishers.ofString(body)).build();
                case "PUT" -> builder.PUT(HttpRequest.BodyPublishers.ofString(body)).build();
                case "DELETE" -> builder.DELETE().build();
                default -> throw new IllegalArgumentException("Unsupported method: " + method);
            };

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                log.warn("GitHub API error ({}): {}", response.statusCode(), response.body());
                return "GitHub API Error (" + response.statusCode() + "): " + response.body();
            }

            return response.body();
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("GitHub API request failed: {}", e.getMessage());
            return "GitHub API Error: " + e.getMessage();
        }
    }

    private String escapeJson(String value) {
        if (value == null) return "null";
        return "\"" + value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t") + "\"";
    }
}
