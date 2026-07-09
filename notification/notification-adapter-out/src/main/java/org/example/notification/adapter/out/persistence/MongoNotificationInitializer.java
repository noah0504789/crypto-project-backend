package org.example.notification.adapter.out.persistence;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

@Component
public class MongoNotificationInitializer implements ApplicationListener<ApplicationReadyEvent> {

    private final MongoTemplate primaryMongoTemplate;

    public MongoNotificationInitializer(
            @Qualifier("primaryMongoTemplate") MongoTemplate primaryMongoTemplate
    ) {
        this.primaryMongoTemplate = primaryMongoTemplate;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!primaryMongoTemplate.collectionExists(MongoNotification.class)) primaryMongoTemplate.createCollection(MongoNotification.class);
        if (!primaryMongoTemplate.collectionExists(MongoNotificationRecipient.class)) primaryMongoTemplate.createCollection(MongoNotificationRecipient.class);
    }
}
