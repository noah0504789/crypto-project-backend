package org.example.chat.chatroom.adapter.in.web.dto;

// TODO: validation check
public record ChatRoomCursor(
        String lastRoomId,
        Long lastPopularity
) {

    public boolean isNull() {
        return lastRoomId == null && lastPopularity == null;
    }
}
