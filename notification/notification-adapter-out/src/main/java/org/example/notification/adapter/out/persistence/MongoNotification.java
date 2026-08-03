package org.example.notification.adapter.out.persistence;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.example.notification.domain.model.Notification;
import org.example.notification.domain.model.NotificationMessagePart;
import org.example.notification.domain.model.NotificationType;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.example.common.time.ServiceTimeConverter;

@Document(collection = "notification")
@CompoundIndexes({
        @CompoundIndex(
                name = "idx_deleted_created",
                def = "{\"deleted\": 1, \"createdAt\": -1}"
        ),
        @CompoundIndex(
                name = "idx_type_deleted_created",
                def = "{\"type\": 1, \"deleted\": 1, \"createdAt\": -1}"
        )
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class MongoNotification {

    @MongoId
    private ObjectId id;

    private NotificationType type;
    private String title;
    private String message;
    private List<MongoNotificationMessagePart> messageParts;
    private String link;
    private Map<String, Object> payload;

    private boolean deleted;
    private Instant deletedAt;

    private Instant createdAt;

    public static MongoNotification fromDomain(Notification notification) {
        return MongoNotification.builder()
                .id(toObjectId(notification.getId()))
                .type(notification.getType())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .messageParts(fromDomainMessageParts(notification.getMessageParts()))
                .link(notification.getLink())
                .payload(copyPayload(notification.getPayload()))
                .deleted(notification.isDeleted())
                .deletedAt(ServiceTimeConverter.toInstant(notification.getDeletedAt()))
                .createdAt(ServiceTimeConverter.toInstant(notification.getCreatedAt()))
                .build();
    }

    public Notification toDomain() {
        return Notification.rehydrate(
                toStringId(id),
                type,
                title,
                message,
                toDomainMessageParts(messageParts),
                link,
                copyPayload(payload),
                deleted,
                ServiceTimeConverter.toLocalDateTime(deletedAt),
                ServiceTimeConverter.toLocalDateTime(createdAt)
        );
    }

    private static List<MongoNotificationMessagePart> fromDomainMessageParts(List<NotificationMessagePart> messageParts) {
        if (messageParts == null) {
            return List.of();
        }

        return messageParts.stream()
                .map(MongoNotificationMessagePart::fromDomain)
                .toList();
    }

    private static List<NotificationMessagePart> toDomainMessageParts(List<MongoNotificationMessagePart> messageParts) {
        if (messageParts == null) {
            return List.of();
        }

        return messageParts.stream()
                .map(MongoNotificationMessagePart::toDomain)
                .toList();
    }

    private static ObjectId toObjectId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }

        return new ObjectId(id);
    }

    private static String toStringId(ObjectId id) {
        if (id == null) {
            return null;
        }

        return id.toHexString();
    }



    private static Map<String, Object> copyPayload(Map<String, Object> payload) {
        if (payload == null) {
            return Map.of();
        }

        return Map.copyOf(payload);
    }
}