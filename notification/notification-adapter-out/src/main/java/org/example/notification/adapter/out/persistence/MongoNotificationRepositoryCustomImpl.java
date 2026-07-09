package org.example.notification.adapter.out.persistence;

import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public class MongoNotificationRepositoryCustomImpl implements MongoNotificationRepositoryCustom {

    private final MongoTemplate secondaryMongoTemplate;

    public MongoNotificationRepositoryCustomImpl(
            @Qualifier("secondaryMongoTemplate") MongoTemplate secondaryMongoTemplate
    ) {
        this.secondaryMongoTemplate = secondaryMongoTemplate;
    }

    @Override
    public List<MongoNotification> findByIdInAndDeletedFalseFromSecondary(Set<ObjectId> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        Query query = new Query()
                .addCriteria(Criteria.where("_id").in(ids))
                .addCriteria(Criteria.where("deleted").is(false));

        return secondaryMongoTemplate.find(query, MongoNotification.class);
    }
}