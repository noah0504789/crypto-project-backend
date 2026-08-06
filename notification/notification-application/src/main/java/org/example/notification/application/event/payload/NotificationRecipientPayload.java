package org.example.notification.application.event.payload;

import com.fasterxml.jackson.annotation.JsonFormat;
import org.example.notification.domain.model.NotificationRecipient;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationRecipientPayload(
        String id,
        String notificationId,
        UUID receiverId,
        boolean read,
        @JsonFormat(pattern = "yyyy/MM/dd HH:mm:ss.SSS")
        LocalDateTime readAt,
        @JsonFormat(pattern = "yyyy/MM/dd HH:mm:ss.SSS")
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
