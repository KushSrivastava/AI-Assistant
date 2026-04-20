package com.knowledgebot.ai.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Pattern;

/**
 * WHY: After webSearch returns a list of URLs, the agent needs to read the
 * actual page content to understand the documentation, code examples, or
 * API reference. This is the "open link" equivalent.
 *
 * HOW: Fetches the URL, strips HTML tags, normalises whitespace.
 * We keep only the text — no markup — so the LLM can read it.
 *
 * LIMITS:
 *   - Content is truncated at 20,000 chars (prevents context overflow).
 *   - We show both HEAD (for intro context) and TAIL (for examples at end).
 *   - 15-second timeout prevents the agent from hanging on slow pages.
 *
 * SECURITY: Only HTTP/HTTPS URLs are allowed (no file://, data://, etc.)
 */
@Component
public class WebPageReaderTool {

    private static final Logger log = LoggerFactory.getLogger(WebPageReaderTool.class);
    private static final int MAX_CONTENT_CHARS = 20_000;

    // Patterns for HTML stripping
    private static final Pattern SCRIPT_STYLE = Pattern.compile(
        "<(script|style)[^>]*>.*?</\\1>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_TAGS = Pattern.compile("<[^>]+>");
    private static final Pattern MULTIPLE_WHITESPACE = Pattern.compile("\\s{3,}");
    private static final Pattern MULTIPLE_NEWLINES = Pattern.compile("\\n{3,}");

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    @Tool(description = """
        Fetch and read the plain-text content of a web page.
        Use this AFTER webSearch to read the full documentation page, blog post, 
        or API reference from a URL returned by search results.
        Content is truncated at 20,000 characters.
        Returns clean text without HTML markup.
        """)
    public String readWebPage(
        @ToolParam(description = "The full URL to read, e.g. 'https://docs.spring.io/spring-ai/reference/api/tools.html'")
        String url
    ) {
        log.info("Reading web page: {}", url);

        // SECURITY: Only allow http/https URLs
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return "ERROR: Only http/https URLs are allowed. Got: " + url;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (Agent Manager Research Bot)")
                .header("Accept", "text/html,application/xhtml+xml,text/plain")
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            int status = response.statusCode();
            if (status == 404) return "ERROR: Page not found (404): " + url;
            if (status == 403) return "ERROR: Access denied (403): " + url + ". The site blocks bots.";
            if (status >= 400) return "ERROR: HTTP " + status + " reading " + url;

            String text = stripHtml(response.body());
            log.debug("Read {} chars from {}", text.length(), url);

            // If content fits, return as-is
            if (text.length() <= MAX_CONTENT_CHARS) {
                return "Content of " + url + ":\n\n" + text;
            }

            // If too large: return head + tail so the LLM sees both the intro and
            // the code examples that often appear at the bottom of docs pages
            int half = MAX_CONTENT_CHARS / 2;
            String head = text.substring(0, half);
            String tail = text.substring(text.length() - half);

            return "Content of " + url + " (truncated — " + text.length() + " total chars):\n\n"
                + "--- BEGINNING OF PAGE ---\n" + head
                + "\n\n... [MIDDLE CONTENT OMITTED] ...\n\n"
                + "--- END OF PAGE ---\n" + tail;

        } catch (java.net.http.HttpTimeoutException e) {
            return "ERROR: Page timed out after 15 seconds: " + url + ". Try a different URL.";
        } catch (Exception e) {
            log.warn("Failed to read web page {}: {}", url, e.getMessage());
            return "ERROR reading " + url + ": " + e.getMessage();
        }
    }

    private String stripHtml(String html) {
        // 1. Remove script + style blocks and their content entirely
        html = SCRIPT_STYLE.matcher(html).replaceAll(" ");
        // 2. Remove all remaining HTML tags
        html = HTML_TAGS.matcher(html).replaceAll(" ");
        // 3. Decode common HTML entities
        html = html.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                   .replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ")
                   .replace("&copy;", "©").replace("&mdash;", "—").replace("&ndash;", "–");
        // 4. Collapse excessive whitespace but preserve readable paragraph breaks
        html = MULTIPLE_WHITESPACE.matcher(html).replaceAll("\n");
        html = MULTIPLE_NEWLINES.matcher(html).replaceAll("\n\n");
        return html.trim();
    }
}
