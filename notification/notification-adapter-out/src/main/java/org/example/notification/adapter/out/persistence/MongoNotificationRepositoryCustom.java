package org.example.notification.adapter.out.persistence;

import org.bson.types.ObjectId;

import java.util.List;
import java.util.Set;

public interface MongoNotificationRepositoryCustom {

    List<MongoNotification> findByIdInAndDeletedFalseFromSecondary(Set<ObjectId> ids);
}
