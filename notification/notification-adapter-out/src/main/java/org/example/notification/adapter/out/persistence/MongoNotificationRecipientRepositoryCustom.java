package org.example.notification.adapter.out.persistence;

import org.bson.types.ObjectId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MongoNotificationRecipientRepositoryCustom {

    List<MongoNotificationRecipient> listLatest(UUID receiverId, int limit);

    List<MongoNotificationRecipient> listPrev(
            UUID receiverId,
            ObjectId lastId,
            Instant lastDeliveredAt,
            int limit
    );

    void saveAllBulk(List<MongoNotificationRecipient> recipients);

    long markAsRead(ObjectId notificationId, UUID receiverId, Instant readAt);
}