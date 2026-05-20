package org.example.chatroom.adapter.dto;

// TODO: validation check
public record ChatRoomCursor(
        String lastId,
        Long lastPopularity
) {

    public boolean isNull() {
        return lastId == null && lastPopularity == null;
    }
}
