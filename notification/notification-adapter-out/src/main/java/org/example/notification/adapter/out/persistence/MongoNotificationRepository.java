package org.example.notification.adapter.out.persistence;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Set;

public interface MongoNotificationRepository extends MongoRepository<MongoNotification, ObjectId>, MongoNotificationRepositoryCustom {

    List<MongoNotification> findByIdInAndDeletedFalse(Set<ObjectId> ids);
}