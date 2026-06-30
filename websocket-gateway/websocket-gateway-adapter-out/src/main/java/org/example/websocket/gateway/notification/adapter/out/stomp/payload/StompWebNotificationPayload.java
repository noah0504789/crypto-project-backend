package org.example.websocket.gateway.notification.adapter.out.stomp.payload;

import org.example.websocket.gateway.notification.application.service.command.WebNotificationCommand;

public record StompWebNotificationPayload(
        String eventType,
        String title,
        String message,
        long createdAt,
        String link,
        String imageUrl,
        Object typedPayload
) {

    public static StompWebNotificationPayload from(WebNotificationCommand command) {
        return new StompWebNotificationPayload(
                command.eventType(),
                command.title(),
                command.message(),
                command.createdAt(),
                command.link(),
                command.imageUrl(),
                command.typedPayload()
        );
    }
}