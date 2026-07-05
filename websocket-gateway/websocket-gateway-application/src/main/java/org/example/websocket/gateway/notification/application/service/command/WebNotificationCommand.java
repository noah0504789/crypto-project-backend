package org.example.websocket.gateway.notification.application.service.command;

import java.util.Map;

public record WebNotificationCommand(
        String receiverId,
        String type,
        String title,
        String body,
        long createdAtMs,
        String link,
        Map<String, Object> data
) {
}