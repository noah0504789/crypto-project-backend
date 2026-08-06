package org.example.notification.adapter.out.persistence;

import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.example.notification.infra.properties.NotificationPersistenceProperties;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class MongoNotificationRecipientRepositoryImpl implements MongoNotificationRecipientRepository {

    private final int batchSize;

    private final MongoTemplate primaryMongoTemplate;
    private final MongoTemplate secondaryMongoTemplate;

    public MongoNotificationRecipientRepositoryImpl(
            @Qualifier("primaryMongoTemplate") MongoTemplate primaryMongoTemplate,
            @Qualifier("secondaryMongoTemplate") MongoTemplate secondaryMongoTemplate,
            NotificationPersistenceProperties persistenceProperties
    ) {
        this.primaryMongoTemplate = primaryMongoTemplate;
        this.secondaryMongoTemplate = secondaryMongoTemplate;
        this.batchSize = persistenceProperties.batchSize();
    }

    @Override
    public List<MongoNotificationRecipient> listLatest(UUID receiverId, int limit) {
        if (receiverId == null || limit <= 0) {
            return List.of();
        }

        Query query = new Query(
                Criteria.where("receiverId").is(receiverId)
        )
                .with(
                        Sort.by(Sort.Direction.DESC, "deliveredAt")
                                .and(Sort.by(Sort.Direction.DESC, "_id"))
                )
                .limit(limit);

        return primaryMongoTemplate.find(query, MongoNotificationRecipient.class);
    }

    @Override
    public List<MongoNotificationRecipient> listHistoricalBefore(
            UUID receiverId,
            ObjectId lastId,
            Instant lastDeliveredAt,
            int limit
    ) {
        if (receiverId == null || lastId == null || lastDeliveredAt == null || limit <= 0) {
            return List.of();
        }

        Criteria base = Criteria.where("receiverId").is(receiverId);

        Criteria cursor = new Criteria().orOperator(
                Criteria.where("deliveredAt").lt(lastDeliveredAt),
                new Criteria().andOperator(
                        Criteria.where("deliveredAt").is(lastDeliveredAt),
                        Criteria.where("_id").lt(lastId)
                )
        );

        Query query = new Query(new Criteria().andOperator(base, cursor))
                .with(
                        Sort.by(Sort.Direction.DESC, "deliveredAt")
                                .and(Sort.by(Sort.Direction.DESC, "_id"))
                )
                .limit(limit);

        return secondaryMongoTemplate.find(query, MongoNotificationRecipient.class);
    }

    @Override
    public void saveAllBulk(List<MongoNotificationRecipient> recipients) {
        if (recipients == null || recipients.isEmpty()) {
            return;
        }

        for (int i = 0; i < recipients.size(); i += batchSize) {
            int end = Math.min(i + batchSize, recipients.size());
            List<MongoNotificationRecipient> batch = recipients.subList(i, end);
            BulkOperations operations = primaryMongoTemplate.bulkOps(
                    BulkOperations.BulkMode.UNORDERED,
                    MongoNotificationRecipient.class
            );

            batch.forEach(recipient -> {
                Update update = new Update()
                        .setOnInsert("_id", recipient.getId())
                        .setOnInsert("notificationId", recipient.getNotificationId())
                        .setOnInsert("receiverId", recipient.getReceiverId())
                        .setOnInsert("read", recipient.isRead())
                        .setOnInsert("readAt", recipient.getReadAt())
                        .setOnInsert("deliveredAt", recipient.getDeliveredAt());

                operations.upsert(
                        Query.query(
                                Criteria.where("notificationId").is(recipient.getNotificationId())
                                        .and("receiverId").is(recipient.getReceiverId())
                        ),
                        update
                );
            });

            operations.execute();
        }
    }

    @Override
    public long markAsRead(ObjectId notificationId, UUID receiverId, Instant readAt) {
        if (notificationId == null || receiverId == null || readAt == null) {
            return 0;
        }

        Query query = new Query(
                Criteria.where("notificationId").is(notificationId)
                        .and("receiverId").is(receiverId)
                        .and("read").is(false)
        );

        Update update = new Update()
                .set("read", true)
                .set("readAt", readAt);

        return primaryMongoTemplate.updateFirst(
                query,
                update,
                MongoNotificationRecipient.class
        ).getModifiedCount();
    }
}
