package org.example.notification.adapter.out.persistence;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MongoNotificationRecipientRepository extends MongoRepository<MongoNotificationRecipient, ObjectId>, MongoNotificationRecipientRepositoryCustom {

}