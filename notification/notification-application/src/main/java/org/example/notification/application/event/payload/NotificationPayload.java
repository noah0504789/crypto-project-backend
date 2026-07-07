package org.example.notification.application.event.payload;

import org.example.notification.domain.model.Notification;
import org.example.notification.domain.model.NotificationMessagePart;
import org.example.notification.domain.model.NotificationType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record NotificationPayload(
        String id,
        String type,
        String title,
        String message,
        List<NotificationMessagePart> messageParts,
        String link,
        Map<String, Object> payload,
        boolean deleted,
        LocalDateTime deletedAt,
        LocalDateTime createdAt
) {

    public NotificationPayload {
        messageParts = messageParts == null ? List.of() : List.copyOf(messageParts);
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }

    public static NotificationPayload from(Notification notification) {
        return new NotificationPayload(
                notification.getId(),
                notification.getType().name(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getMessageParts(),
                notification.getLink(),
                notification.getPayload(),
                notification.isDeleted(),
                notification.getDeletedAt(),
                notification.getCreatedAt()
        );
    }

    public Notification toDomain() {
        return Notification.rehydrate(
                id,
                NotificationType.valueOf(type),
                title,
                message,
                messageParts,
                link,
                payload,
                deleted,
                deletedAt,
                createdAt
        );
    }
}