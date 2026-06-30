package org.example.websocket.gateway.notification.application.service.command;

public record WebNotificationCommand(
        String receiverId,
        String eventType,
        String title,
        String message,
        long createdAt,
        String link,
        String imageUrl,
        Object typedPayload
) {
}