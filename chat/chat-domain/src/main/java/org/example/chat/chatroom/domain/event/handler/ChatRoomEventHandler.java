package org.example.chat.chatroom.domain.event.handler;

import org.example.chat.chatroom.domain.event.ChatRoomActiveEvent;
import org.example.chat.chatroom.domain.event.ChatRoomCacheActivityInvalidateEvent;
import org.example.chat.chatroom.domain.event.ChatRoomCacheDeleteEvent;
import org.example.chat.chatroom.domain.event.ChatRoomCacheInfoInvalidateEvent;
import org.example.chat.chatroom.domain.event.ChatRoomCacheSaveEvent;
import org.example.chat.chatroom.domain.event.ChatRoomCacheUpdateEvent;
import org.example.chat.chatroom.domain.event.ChatRoomDeletedEvent;
import org.example.chat.chatroom.domain.event.ChatRoomJoinedEvent;
import org.example.chat.chatroom.domain.event.ChatRoomLeavedEvent;
import org.example.chat.chatroom.domain.event.ChatRoomPersistedEvent;
import org.example.chat.chatroom.domain.event.ChatRoomUpdatedEvent;

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
