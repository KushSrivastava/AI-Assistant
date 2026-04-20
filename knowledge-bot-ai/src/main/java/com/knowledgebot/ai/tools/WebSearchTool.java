package com.knowledgebot.ai.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * WHY: The LLM's training data has a cutoff date — it doesn't know about
 * library versions released last month, latest Spring Boot 4 APIs, or new
 * security advisories. Web search closes this gap.
 *
 * HOW: DuckDuckGo HTML endpoint requires no API key. We fetch the HTML,
 * parse result snippets with regex, and return a clean numbered list.
 *
 * ALTERNATIVE: Self-host SearXNG via Docker for richer results.
 * docker run -d -p 8888:8080 searxng/searxng
 * Then change the URL to http://localhost:8888/search?q=...&format=json
 *
 * LIMITS: Results are capped at 5 to prevent context overflow.
 * Snippets are truncated to 300 chars each.
 */
@Component
public class WebSearchTool {

    private static final Logger log = LoggerFactory.getLogger(WebSearchTool.class);
    private static final int MAX_RESULTS = 5;
    private static final int SNIPPET_MAX_LENGTH = 300;

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    // DuckDuckGo HTML result patterns
    // Title: <a class="result__a" href="...">TITLE</a>
    // URL:   <a class="result__url" ...>URL</a>
    // Snip:  <a class="result__snippet" ...>SNIPPET</a>
    private static final Pattern RESULT_BLOCK   = Pattern.compile(
        "<div class=\"result[^\"]*\".*?(?=<div class=\"result|$)", Pattern.DOTALL);
    private static final Pattern TITLE_PATTERN  = Pattern.compile(
        "<a[^>]+class=\"result__a\"[^>]*>(.*?)</a>", Pattern.DOTALL);
    private static final Pattern URL_PATTERN    = Pattern.compile(
        "<a[^>]+class=\"result__url\"[^>]*>\\s*(https?://[^<\\s]+)\\s*</a>", Pattern.DOTALL);
    private static final Pattern SNIPPET_PATTERN = Pattern.compile(
        "<a[^>]+class=\"result__snippet\"[^>]*>(.*?)</a>", Pattern.DOTALL);
    private static final Pattern TAGS_PATTERN   = Pattern.compile("<[^>]+>");

    @Tool(description = """
        Search the internet for information using DuckDuckGo (no API key required).
        Use this when you need to:
        - Look up library documentation or API usage
        - Find code examples for unfamiliar frameworks
        - Check for the latest version of a dependency
        - Research best practices or security advisories
        Returns the top 5 results with titles, URLs, and snippets.
        After getting results, use readWebPage to read the full content of a promising URL.
        """)
    public String webSearch(
        @ToolParam(description = "The search query. Be specific, e.g. 'Spring Boot 4 @Tool annotation example site:docs.spring.io'")
        String query
    ) {
        log.info("Web search: {}", query);
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = "https://html.duckduckgo.com/html/?q=" + encodedQuery;

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                // Mimic a browser to avoid bot-blocking
                .header("User-Agent", "Mozilla/5.0 (Agent Manager Research Bot)")
                .header("Accept", "text/html,application/xhtml+xml")
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() != 200) {
                return "ERROR: DuckDuckGo returned HTTP " + response.statusCode()
                    + ". Try a more specific query.";
            }

            String results = parseSearchResults(response.body());
            log.debug("Search complete for '{}' — returning results", query);
            return results;

        } catch (Exception e) {
            log.warn("Web search failed for '{}': {}", query, e.getMessage());
            return "ERROR: Web search failed (" + e.getMessage()
                + "). Check internet connectivity or try a different query.";
        }
    }

    private String parseSearchResults(String html) {
        List<String> formatted = new ArrayList<>();
        Matcher blockMatcher = RESULT_BLOCK.matcher(html);
        int count = 0;

        while (blockMatcher.find() && count < MAX_RESULTS) {
            String block = blockMatcher.group();

            String title   = extractGroup(TITLE_PATTERN,   block);
            String pageUrl = extractGroup(URL_PATTERN,     block);
            String snippet = extractGroup(SNIPPET_PATTERN, block);

            if (title.isBlank() || pageUrl.isBlank()) continue;

            title   = cleanHtml(title);
            snippet = cleanHtml(snippet);

            if (snippet.length() > SNIPPET_MAX_LENGTH) {
                snippet = snippet.substring(0, SNIPPET_MAX_LENGTH) + "…";
            }

            formatted.add(String.format("%d. **%s**%n   %s%n   %s",
                count + 1, title, pageUrl.trim(), snippet));
            count++;
        }

        if (formatted.isEmpty()) {
            return "No search results found. DuckDuckGo may have changed its HTML structure, "
                 + "or the query returned no results. Try rewording the query.";
        }

        return "Search results for query:\n\n"
            + String.join("\n\n", formatted)
            + "\n\n(Use readWebPage to read the full content from any URL above)";
    }

    private String extractGroup(Pattern pattern, String input) {
        Matcher m = pattern.matcher(input);
        return m.find() ? m.group(1) : "";
    }

    private String cleanHtml(String html) {
        // Remove tags, decode common HTML entities, normalize whitespace
        return TAGS_PATTERN.matcher(html)
            .replaceAll("")
            .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }
}
