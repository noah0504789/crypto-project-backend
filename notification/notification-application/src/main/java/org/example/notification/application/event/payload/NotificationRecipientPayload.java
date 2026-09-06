package org.example.notification.application.event.payload;

import com.fasterxml.jackson.annotation.JsonFormat;
import org.example.notification.domain.model.NotificationRecipient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

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
            String id,
            String notificationId,
            UUID receiverId,
            LocalDateTime deliveredAt
    ) {
        return new NotificationRecipientPayload(
                id,
                notificationId,
                receiverId,
                false,
                null,
                deliveredAt
        );
    }

    public static List<NotificationRecipientPayload> forReceivers(
            String notificationId,
            List<UUID> receiverIds,
            LocalDateTime deliveredAt,
            Supplier<String> idGenerator
    ) {
        return receiverIds.stream()
                .map(receiverId -> of(idGenerator.get(), notificationId, receiverId, deliveredAt))
                .toList();
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
