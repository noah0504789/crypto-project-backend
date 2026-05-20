package org.example.chatroom.adapter.dto;

import java.time.Instant;

// TODO: validation check
public record MyChatRoomCursor(
        Boolean lastUnreadFlag,
        Instant lastMsgCreatedAt,
        String lastId
) {

    public boolean isNull() {
        return lastUnreadFlag == null && lastMsgCreatedAt == null && lastId == null;
    }
}
