package org.example.chatroom.domain.port;

import org.example.chatroom.domain.event.ChatRoomActiveEvent;
import org.example.chatroom.domain.event.ChatRoomCacheActivityInvalidateEvent;
import org.example.chatroom.domain.event.ChatRoomCacheDeleteEvent;
import org.example.chatroom.domain.event.ChatRoomCacheInfoInvalidateEvent;
import org.example.chatroom.domain.event.ChatRoomCacheSaveEvent;
import org.example.chatroom.domain.event.ChatRoomCacheUpdateEvent;
import org.example.chatroom.domain.event.ChatRoomDeletedEvent;
import org.example.chatroom.domain.event.ChatRoomJoinedEvent;
import org.example.chatroom.domain.event.ChatRoomLeavedEvent;
import org.example.chatroom.domain.event.ChatRoomPersistedEvent;
import org.example.chatroom.domain.event.ChatRoomUpdatedEvent;

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
