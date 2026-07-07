package org.example.chat.chatroom.application.port.in;

import org.example.chat.chatroom.application.event.ChatRoomActiveEvent;
import org.example.chat.chatroom.application.event.ChatRoomCacheActivityInvalidateEvent;
import org.example.chat.chatroom.application.event.ChatRoomCacheDeleteEvent;
import org.example.chat.chatroom.application.event.ChatRoomCacheInfoInvalidateEvent;
import org.example.chat.chatroom.application.event.ChatRoomCacheSaveEvent;
import org.example.chat.chatroom.application.event.ChatRoomCacheUpdateEvent;
import org.example.chat.chatroom.application.event.ChatRoomDeletedEvent;
import org.example.chat.chatroom.application.event.ChatRoomJoinedEvent;
import org.example.chat.chatroom.application.event.ChatRoomLeavedEvent;
import org.example.chat.chatroom.application.event.ChatRoomPersistedEvent;
import org.example.chat.chatroom.application.event.ChatRoomUpdatedEvent;

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
