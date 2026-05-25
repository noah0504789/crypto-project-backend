package org.example.chat.chatroom.domain.port;

import org.example.chat.chatroom.domain.event.dlq.ChatRoomActiveDlqEvent;
import org.example.chat.chatroom.domain.event.dlq.ChatRoomCacheActivityInvalidateDlqEvent;
import org.example.chat.chatroom.domain.event.dlq.ChatRoomCacheDeleteDlqEvent;
import org.example.chat.chatroom.domain.event.dlq.ChatRoomCacheInfoInvalidateDlqEvent;
import org.example.chat.chatroom.domain.event.dlq.ChatRoomCacheSaveDlqEvent;
import org.example.chat.chatroom.domain.event.dlq.ChatRoomCacheUpdateDlqEvent;
import org.example.chat.chatroom.domain.event.dlq.ChatRoomDeletedDlqEvent;
import org.example.chat.chatroom.domain.event.dlq.ChatRoomJoinedDlqEvent;
import org.example.chat.chatroom.domain.event.dlq.ChatRoomLeavedDlqEvent;
import org.example.chat.chatroom.domain.event.dlq.ChatRoomPersistedDlqEvent;
import org.example.chat.chatroom.domain.event.dlq.ChatRoomUpdatedDlqEvent;

public interface ChatRoomDlqHandler {
    void handle(ChatRoomPersistedDlqEvent event);
    void handle(ChatRoomUpdatedDlqEvent event);
    void handle(ChatRoomDeletedDlqEvent event);
    void handle(ChatRoomJoinedDlqEvent event);
    void handle(ChatRoomLeavedDlqEvent event);
    void handle(ChatRoomActiveDlqEvent event);
    void handle(ChatRoomCacheSaveDlqEvent event);
    void handle(ChatRoomCacheUpdateDlqEvent event);
    void handle(ChatRoomCacheDeleteDlqEvent event);
    void handle(ChatRoomCacheActivityInvalidateDlqEvent event);
    void handle(ChatRoomCacheInfoInvalidateDlqEvent event);
}
