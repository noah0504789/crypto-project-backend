package org.example.notification.domain.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class NotificationRecipient {

    private String id;
    private String notificationId;
    private UUID receiverId;

    private boolean read;
    private LocalDateTime readAt;

    private LocalDateTime deliveredAt;

    public static NotificationRecipient rehydrate(
            String id,
            String notificationId,
            UUID receiverId,
            boolean read,
            LocalDateTime readAt,
            LocalDateTime deliveredAt
    ) {
        return NotificationRecipient.builder()
                .id(id)
                .notificationId(notificationId)
                .receiverId(receiverId)
                .read(read)
                .readAt(readAt)
                .deliveredAt(deliveredAt)
                .build();
    }
}