package org.example.chat.chatroom.adapter.in.web.dto;

// TODO: validation check
public record MyChatRoomCursor(
        String lastRoomId,
        Boolean lastUnreadFlag,
        Long lastMsgCreatedAtMs
) {

    public boolean isNull() {
        return lastRoomId == null
                || lastRoomId.isBlank()
                || lastUnreadFlag == null
                || lastMsgCreatedAtMs == null;
    }
}