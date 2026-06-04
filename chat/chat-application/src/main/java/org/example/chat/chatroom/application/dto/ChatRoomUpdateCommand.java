package org.example.chat.chatroom.application.dto;

import org.example.chat.chatroom.domain.event.payload.ChatRoomUpdatedPayload;
import org.example.chat.chatroom.domain.model.ChatRoomCategory;

import java.util.HashMap;
import java.util.Map;

public record ChatRoomUpdateCommand(
        String title,
        String description,
        ChatRoomCategory category
) {

    public boolean isEmpty() {
        return title == null
                && description == null
                && category == null;
    }

    public ChatRoomUpdatedPayload toPayload() {
        return new ChatRoomUpdatedPayload(title, description, category);
    }
}