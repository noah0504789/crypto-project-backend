package org.example.chat.chatroom.adapter.out.persistence;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

@Component
public class MongoChatRoomInitializer implements ApplicationListener<ApplicationReadyEvent> {

    private final MongoTemplate chatMongoTemplate;

    public MongoChatRoomInitializer(
            @Qualifier("primaryMongoTemplate") MongoTemplate chatMongoTemplate
    ) {
        this.chatMongoTemplate = chatMongoTemplate;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!chatMongoTemplate.collectionExists(MongoChatRoom.class)) chatMongoTemplate.createCollection(MongoChatRoom.class);
        if (!chatMongoTemplate.collectionExists(MongoChatRoomMembership.class)) chatMongoTemplate.createCollection(MongoChatRoomMembership.class);
    }
}
