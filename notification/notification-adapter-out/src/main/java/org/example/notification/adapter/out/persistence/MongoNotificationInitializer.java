package org.example.notification.adapter.out.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MongoNotificationInitializer implements ApplicationListener<ApplicationReadyEvent> {

    @Qualifier("mongoTemplate")
    private final MongoTemplate chatMongoTemplate;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!chatMongoTemplate.collectionExists(MongoNotification.class)) chatMongoTemplate.createCollection(MongoNotification.class);
        if (!chatMongoTemplate.collectionExists(MongoNotificationRecipient.class)) chatMongoTemplate.createCollection(MongoNotificationRecipient.class);
    }
}
