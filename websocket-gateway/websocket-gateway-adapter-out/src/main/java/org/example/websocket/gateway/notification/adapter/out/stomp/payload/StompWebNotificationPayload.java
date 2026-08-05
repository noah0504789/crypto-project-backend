package org.example.websocket.gateway.notification.adapter.out.stomp.payload;

import org.example.websocket.gateway.notification.application.service.command.WebNotificationCommand;

import java.util.Map;

public record StompWebNotificationPayload(
        String notificationId,
        String type,
        String title,
        String body,
        long createdAtMs,
        String link,
        Map<String, Object> data
) {

    public static StompWebNotificationPayload from(WebNotificationCommand command) {
        return new StompWebNotificationPayload(
                command.notificationId(),
                command.type(),
                command.title(),
                command.body(),
                command.createdAtMs(),
                command.link(),
                command.data()
        );
    }
}
