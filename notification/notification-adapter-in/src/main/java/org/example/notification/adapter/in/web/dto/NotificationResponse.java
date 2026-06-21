package org.example.notification.adapter.in.web.dto;

import org.example.notification.domain.model.NotificationMessagePart;
import org.example.notification.application.service.query.NotificationInboxItem;

import java.time.LocalDateTime;
import java.util.List;

public record NotificationResponse(
        String id,
        String recipientId,
        String title,
        String message,
        List<NotificationMessagePartResponse> messageParts,
        boolean read,
        String readAt,
        String deliveredAt,
        String createdAt,
        String link
) {

    public static NotificationResponse from(NotificationInboxItem item) {
        return new NotificationResponse(
                item.notificationId(),
                item.recipientId(),
                item.title(),
                item.message(),
                toMessagePartResponses(item.messageParts()),
                item.read(),
                toString(item.readAt()),
                toString(item.deliveredAt()),
                toString(item.createdAt()),
                item.link()
        );
    }

    private static List<NotificationMessagePartResponse> toMessagePartResponses(List<NotificationMessagePart> messageParts) {
        if (messageParts == null) {
            return List.of();
        }

        return messageParts.stream()
                .map(NotificationMessagePartResponse::from)
                .toList();
    }

    private static String toString(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }

        return dateTime.toString();
    }
}