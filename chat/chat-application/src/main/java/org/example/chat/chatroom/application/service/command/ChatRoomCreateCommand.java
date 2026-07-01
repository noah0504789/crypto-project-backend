package org.example.chat.chatroom.application.service.command;

import org.example.chat.chatroom.domain.model.ChatRoomCategory;

public record ChatRoomCreateCommand(
        String hostId,
        String title,
        String description,
        ChatRoomCategory category
) {
}