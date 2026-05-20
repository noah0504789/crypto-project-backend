package org.example.chatroom.domain.port;

import org.example.chatroom.domain.model.event.dlq.ChatRoomActiveDlqEvent;
import org.example.chatroom.domain.model.event.dlq.ChatRoomCacheActivityInvalidateDlqEvent;
import org.example.chatroom.domain.model.event.dlq.ChatRoomCacheDeleteDlqEvent;
import org.example.chatroom.domain.model.event.dlq.ChatRoomCacheInfoInvalidateDlqEvent;
import org.example.chatroom.domain.model.event.dlq.ChatRoomCacheSaveDlqEvent;
import org.example.chatroom.domain.model.event.dlq.ChatRoomCacheUpdateDlqEvent;
import org.example.chatroom.domain.model.event.dlq.ChatRoomDeletedDlqEvent;
import org.example.chatroom.domain.model.event.dlq.ChatRoomJoinedDlqEvent;
import org.example.chatroom.domain.model.event.dlq.ChatRoomLeavedDlqEvent;
import org.example.chatroom.domain.model.event.dlq.ChatRoomPersistedDlqEvent;
import org.example.chatroom.domain.model.event.dlq.ChatRoomUpdatedDlqEvent;

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
