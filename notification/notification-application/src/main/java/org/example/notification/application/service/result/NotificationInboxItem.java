package org.example.notification.application.service.result;

import org.example.notification.domain.model.Notification;
import org.example.notification.domain.model.NotificationMessagePart;
import org.example.notification.domain.model.NotificationRecipient;
import org.example.notification.domain.model.NotificationType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record NotificationInboxItem(
        String notificationId,
        String recipientId,
        NotificationType type,
        String title,
        String message,
        List<NotificationMessagePart> messageParts,
        String link,
        Map<String, Object> payload,
        boolean read,
        LocalDateTime readAt,
        LocalDateTime deliveredAt,
        LocalDateTime createdAt
) {

    public NotificationInboxItem {
        messageParts = messageParts == null ? List.of() : List.copyOf(messageParts);
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }

    public static NotificationInboxItem of(Notification notification, NotificationRecipient recipient) {
        return new NotificationInboxItem(
                notification.getId(),
                recipient.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getMessageParts(),
                notification.getLink(),
                notification.getPayload(),
                recipient.isRead(),
                recipient.getReadAt(),
                recipient.getDeliveredAt(),
                notification.getCreatedAt()
        );
    }
}