package org.example.notification.adapter.out.persistence;

import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class MongoNotificationRecipientRepositoryImpl implements MongoNotificationRecipientRepositoryCustom {

    private static final int BATCH_SIZE = 1_000;
    private final MongoTemplate mongoTemplate;

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

        return mongoTemplate.find(query, MongoNotificationRecipient.class);
    }

    @Override
    public List<MongoNotificationRecipient> listPrev(
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

        return mongoTemplate.find(query, MongoNotificationRecipient.class);
    }

    @Override
    public void saveAllBulk(List<MongoNotificationRecipient> recipients) {
        if (recipients == null || recipients.isEmpty()) {
            return;
        }

        for (int i = 0; i < recipients.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, recipients.size());

            List<MongoNotificationRecipient> batch = recipients.subList(i, end);

            mongoTemplate.bulkOps(
                            BulkOperations.BulkMode.UNORDERED,
                            MongoNotificationRecipient.class
                    )
                    .insert(batch)
                    .execute();
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

        return mongoTemplate.updateFirst(query, update, MongoNotificationRecipient.class).getModifiedCount();
    }
}