package org.example.websocket.gateway.notification.application.service.command;

import org.example.notification.contract.event.PriceAlertPayload;
import org.example.notification.contract.event.WebNotificationMessagePart;

import java.util.List;

public record WebNotificationCommand(
        String receiverId,
        String notificationId,
        String type,
        String title,
        String body,
        long createdAtMs,
        String link,
        List<WebNotificationMessagePart> messageParts,
        PriceAlertPayload data
) {
}
