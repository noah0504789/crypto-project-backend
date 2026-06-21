package org.example.notification.domain.event;

import org.example.notification.domain.model.NotificationRecipient;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationRecipientPayload(
        String id,
        String notificationId,
        UUID receiverId,
        boolean read,
        LocalDateTime readAt,
        LocalDateTime deliveredAt
) {

    public static NotificationRecipientPayload from(NotificationRecipient recipient) {
        return new NotificationRecipientPayload(
                recipient.getId(),
                recipient.getNotificationId(),
                recipient.getReceiverId(),
                recipient.isRead(),
                recipient.getReadAt(),
                recipient.getDeliveredAt()
        );
    }

    public NotificationRecipient toDomain() {
        return NotificationRecipient.rehydrate(
                id,
                notificationId,
                receiverId,
                read,
                readAt,
                deliveredAt
        );
    }
}