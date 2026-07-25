package org.example.chat.chatroom.application.service.command;

import org.example.chat.chatroom.application.event.payload.ChatRoomUpdatedPayload;
import org.example.chat.chatroom.domain.model.ChatRoomCategory;

public record ChatRoomUpdateCommand(
        String roomId,
        String myUserId,
        String title,
        String description,
        ChatRoomCategory category
) {

    public ChatRoomUpdatedPayload toPayload() {
        return new ChatRoomUpdatedPayload(title, description, category);
    }
}