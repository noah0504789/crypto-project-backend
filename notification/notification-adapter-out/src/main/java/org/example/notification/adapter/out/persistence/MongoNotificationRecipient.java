package org.example.notification.adapter.out.persistence;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.example.notification.domain.model.NotificationRecipient;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.example.common.time.ServiceZoneUtils.ZONE_ID;

@Document(collection = "notification_recipient")
@CompoundIndexes({
        @CompoundIndex(
                name = "ux_notification_recipient_notification_receiver",
                def = "{\"notificationId\": 1, \"receiverId\": 1}",
                unique = true
        ),
        @CompoundIndex(
                name = "idx_receiver_delivered",
                def = "{\"receiverId\": 1, \"deliveredAt\": -1}"
        ),
        @CompoundIndex(
                name = "idx_receiver_read_delivered",
                def = "{\"receiverId\": 1, \"read\": 1, \"deliveredAt\": -1}"
        ),
        @CompoundIndex(
                name = "idx_notification",
                def = "{\"notificationId\": 1}"
        )
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class MongoNotificationRecipient {

    @MongoId
    private ObjectId id;

    private ObjectId notificationId;
    private UUID receiverId;

    private boolean read;
    private Instant readAt;

    private Instant deliveredAt;

    public static MongoNotificationRecipient fromDomain(NotificationRecipient recipient) {
        return MongoNotificationRecipient.builder()
                .id(toObjectId(recipient.getId()))
                .notificationId(toObjectId(recipient.getNotificationId()))
                .receiverId(recipient.getReceiverId())
                .read(recipient.isRead())
                .readAt(toInstant(recipient.getReadAt()))
                .deliveredAt(toInstant(recipient.getDeliveredAt()))
                .build();
    }

    public NotificationRecipient toDomain() {
        return NotificationRecipient.rehydrate(
                toStringId(id),
                toStringId(notificationId),
                receiverId,
                read,
                toLocalDateTime(readAt),
                toLocalDateTime(deliveredAt)
        );
    }

    private static String toStringId(ObjectId id) {
        if (id == null) {
            return null;
        }

        return id.toHexString();
    }

    private static LocalDateTime toLocalDateTime(Instant instant) {
        if (instant == null) {
            return null;
        }

        return LocalDateTime.ofInstant(instant, ZONE_ID);
    }

    private static ObjectId toObjectId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }

        return new ObjectId(id);
    }

    private static Instant toInstant(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }

        return dateTime.atZone(ZONE_ID).toInstant();
    }
}