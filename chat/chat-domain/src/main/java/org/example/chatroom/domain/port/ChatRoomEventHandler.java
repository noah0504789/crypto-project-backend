package org.example.chatroom.domain.port;

import org.example.chatroom.domain.model.event.ChatRoomActiveEvent;
import org.example.chatroom.domain.model.event.ChatRoomCacheActivityInvalidateEvent;
import org.example.chatroom.domain.model.event.ChatRoomCacheDeleteEvent;
import org.example.chatroom.domain.model.event.ChatRoomCacheInfoInvalidateEvent;
import org.example.chatroom.domain.model.event.ChatRoomCacheSaveEvent;
import org.example.chatroom.domain.model.event.ChatRoomCacheUpdateEvent;
import org.example.chatroom.domain.model.event.ChatRoomDeletedEvent;
import org.example.chatroom.domain.model.event.ChatRoomJoinedEvent;
import org.example.chatroom.domain.model.event.ChatRoomLeavedEvent;
import org.example.chatroom.domain.model.event.ChatRoomPersistedEvent;
import org.example.chatroom.domain.model.event.ChatRoomUpdatedEvent;

public interface ChatRoomEventHandler {
    void handle(ChatRoomPersistedEvent event, String txId);
    void handle(ChatRoomUpdatedEvent event, String txId);
    void handle(ChatRoomJoinedEvent event, String txId);
    void handle(ChatRoomLeavedEvent event, String txId);
    void handle(ChatRoomDeletedEvent event, String txId);
    void handle(ChatRoomActiveEvent event, String txId);
    void handle(ChatRoomCacheSaveEvent event, String txId);
    void handle(ChatRoomCacheUpdateEvent event, String txId);
    void handle(ChatRoomCacheDeleteEvent event, String txId);
    void handle(ChatRoomCacheActivityInvalidateEvent event, String txId);
    void handle(ChatRoomCacheInfoInvalidateEvent event, String txId);
}
