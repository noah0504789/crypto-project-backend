package org.example.notification.application.event.payload;

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

    public static NotificationRecipientPayload of(
            String notificationId,
            UUID receiverId,
            LocalDateTime deliveredAt
    ) {
        return new NotificationRecipientPayload(
                null,
                notificationId,
                receiverId,
                false,
                null,
                deliveredAt
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