package com.knowledgebot.ai.notifications;

public record NotificationEvent(
    String eventType,
    String title,
    String message,
    String severity,
    long timestamp
) {
    public static NotificationEvent info(String title, String message) {
        return new NotificationEvent("info", title, message, "info", System.currentTimeMillis());
    }

    public static NotificationEvent success(String title, String message) {
        return new NotificationEvent("success", title, message, "success", System.currentTimeMillis());
    }

    public static NotificationEvent warning(String title, String message) {
        return new NotificationEvent("warning", title, message, "warning", System.currentTimeMillis());
    }

    public static NotificationEvent error(String title, String message) {
        return new NotificationEvent("error", title, message, "error", System.currentTimeMillis());
    }
}
