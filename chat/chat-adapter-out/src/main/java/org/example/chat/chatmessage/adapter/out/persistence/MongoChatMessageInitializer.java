package org.example.chat.chatmessage.adapter.out.persistence;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

@Component
public class MongoChatMessageInitializer implements ApplicationListener<ApplicationReadyEvent> {

    private final MongoTemplate chatMongoTemplate;

    public MongoChatMessageInitializer(
            @Qualifier("primaryMongoTemplate") MongoTemplate chatMongoTemplate
    ) {
        this.chatMongoTemplate = chatMongoTemplate;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (chatMongoTemplate.collectionExists(MongoChatMessage.class)) return;

        chatMongoTemplate.createCollection(MongoChatMessage.class);
    }
}
