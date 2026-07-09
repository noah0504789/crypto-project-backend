package org.example.notification.adapter.out.persistence;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface MongoNotificationRepository extends MongoRepository<MongoNotification, ObjectId>, MongoNotificationRepositoryCustom {

    List<MongoNotification> findByIdInAndDeletedFalse(Set<ObjectId> ids);
}