package org.example.websocket.gateway.notification.adapter.out.stomp.payload;

import org.example.notification.contract.event.PriceAlertData;
import org.example.notification.contract.event.WebNotificationMessagePart;
import org.example.websocket.gateway.notification.application.service.command.WebNotificationCommand;

import java.util.List;

public record StompWebNotificationPayload(
        String notificationId,
        String type,
        String title,
        String body,
        long createdAtMs,
        String link,
        List<WebNotificationMessagePart> messageParts,
        PriceAlertData data
) {

    public static StompWebNotificationPayload from(WebNotificationCommand command) {
        return new StompWebNotificationPayload(
                command.notificationId(),
                command.type(),
                command.title(),
                command.body(),
                command.createdAtMs(),
                command.link(),
                command.messageParts(),
                command.data()
        );
    }
}
