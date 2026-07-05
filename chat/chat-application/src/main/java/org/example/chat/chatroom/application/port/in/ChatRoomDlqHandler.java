package org.example.chat.chatroom.application.port.in;

import org.example.chat.chatroom.application.event.dlq.*;

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
