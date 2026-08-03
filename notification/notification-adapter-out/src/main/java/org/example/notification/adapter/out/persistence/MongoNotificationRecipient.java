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
import java.util.UUID;

import org.example.common.time.ServiceTimeConverter;

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
                .readAt(ServiceTimeConverter.toInstant(recipient.getReadAt()))
                .deliveredAt(ServiceTimeConverter.toInstant(recipient.getDeliveredAt()))
                .build();
    }

    public NotificationRecipient toDomain() {
        return NotificationRecipient.rehydrate(
                toStringId(id),
                toStringId(notificationId),
                receiverId,
                read,
                ServiceTimeConverter.toLocalDateTime(readAt),
                ServiceTimeConverter.toLocalDateTime(deliveredAt)
        );
    }

    private static String toStringId(ObjectId id) {
        if (id == null) {
            return null;
        }

        return id.toHexString();
    }


    private static ObjectId toObjectId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }

        return new ObjectId(id);
    }

}