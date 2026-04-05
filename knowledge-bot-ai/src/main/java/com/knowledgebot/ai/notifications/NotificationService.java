package com.knowledgebot.ai.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Value("${knowledge-bot.notifications.discord-webhook:}")
    private String discordWebhookUrl;

    @Value("${knowledge-bot.notifications.telegram-bot-token:}")
    private String telegramBotToken;

    @Value("${knowledge-bot.notifications.telegram-chat-id:}")
    private String telegramChatId;

    @Value("${knowledge-bot.notifications.webhook-url:}")
    private String genericWebhookUrl;

    private final List<NotificationEvent> eventHistory = new ArrayList<>();

    public void sendDiscord(NotificationEvent event) {
        if (discordWebhookUrl == null || discordWebhookUrl.isEmpty()) {
            log.warn("Discord webhook not configured, skipping notification");
            return;
        }

        int color = switch (event.severity()) {
            case "success" -> 5763719;
            case "warning" -> 15105570;
            case "error" -> 15548997;
            default -> 3447003;
        };

        String jsonPayload = """
            {
                "embeds": [{
                    "title": "%s",
                    "description": "%s",
                    "color": %d,
                    "footer": {"text": "Knowledge Bot | %s"},
                    "fields": [
                        {"name": "Type", "value": "%s", "inline": true},
                        {"name": "Time", "value": "<t:%d:f>", "inline": true}
                    ]
                }]
            }
            """.formatted(
                escapeJson(event.title()),
                escapeJson(event.message()),
                color,
                event.eventType(),
                event.severity(),
                event.timestamp() / 1000
            );

        sendAsync(discordWebhookUrl, jsonPayload, "Discord");
    }

    public void sendTelegram(NotificationEvent event) {
        if (telegramBotToken == null || telegramBotToken.isEmpty() || telegramChatId == null || telegramChatId.isEmpty()) {
            log.warn("Telegram bot not configured, skipping notification");
            return;
        }

        String emoji = switch (event.severity()) {
            case "success" -> "\u2705";
            case "warning" -> "\u26A0\uFE0F";
            case "error" -> "\u274C";
            default -> "\u2139\uFE0F";
        };

        String message = String.format("%s *%s*\n\n%s\n\n_Type: %s_",
                emoji, event.title(), event.message(), event.eventType());

        String jsonPayload = """
            {
                "chat_id": "%s",
                "text": "%s",
                "parse_mode": "Markdown"
            }
            """.formatted(telegramChatId, escapeJson(message));

        String url = "https://api.telegram.org/bot" + telegramBotToken + "/sendMessage";
        sendAsync(url, jsonPayload, "Telegram");
    }

    public void sendWebhook(NotificationEvent event) {
        if (genericWebhookUrl == null || genericWebhookUrl.isEmpty()) {
            log.warn("Generic webhook not configured, skipping notification");
            return;
        }

        String jsonPayload = """
            {
                "event": "%s",
                "title": "%s",
                "message": "%s",
                "severity": "%s",
                "timestamp": %d
            }
            """.formatted(event.eventType(), escapeJson(event.title()), escapeJson(event.message()), event.severity(), event.timestamp());

        sendAsync(genericWebhookUrl, jsonPayload, "Webhook");
    }

    public void sendToAll(NotificationEvent event) {
        eventHistory.add(event);
        sendDiscord(event);
        sendTelegram(event);
        sendWebhook(event);
        log.info("Notification sent: [{}] {}", event.severity().toUpperCase(), event.title());
    }

    public void notifyTaskComplete(String taskName, String result) {
        sendToAll(NotificationEvent.success("Task Complete", taskName + "\n" + result));
    }

    public void notifyTaskFailed(String taskName, String error) {
        sendToAll(NotificationEvent.error("Task Failed", taskName + "\nError: " + error));
    }

    public void notifyPlanGenerated(String goal) {
        sendToAll(NotificationEvent.info("Plan Generated", "New plan created for: " + goal));
    }

    public void notifyDeploymentComplete(String projectName, String environment) {
        sendToAll(NotificationEvent.success("Deployment Complete", projectName + " deployed to " + environment));
    }

    public void notifyDeploymentFailed(String projectName, String error) {
        sendToAll(NotificationEvent.error("Deployment Failed", projectName + "\nError: " + error));
    }

    public void notifyTestFailure(String testName, String failureDetails) {
        sendToAll(NotificationEvent.warning("Test Failure", testName + "\n" + failureDetails));
    }

    public List<NotificationEvent> getEventHistory() {
        return List.copyOf(eventHistory);
    }

    public int getNotificationCount() {
        return eventHistory.size();
    }

    private void sendAsync(String url, String jsonPayload, String channel) {
        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                        .timeout(Duration.ofSeconds(10))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() >= 400) {
                    log.warn("{} notification failed ({}): {}", channel, response.statusCode(), response.body());
                } else {
                    log.info("{} notification sent successfully", channel);
                }
            } catch (IOException | InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("{} notification error: {}", channel, e.getMessage());
            }
        });
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
