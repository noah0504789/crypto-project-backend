package org.example.chat.chatroom.application.event.payload;

import org.example.chat.chatroom.domain.model.ChatRoomCategory;

import java.time.Instant;
import java.util.Set;

public record ChatRoomPersistPayload(
        String id,
        String hostId,
        String title,
        String description,
        ChatRoomCategory category,
        Set<String> memberIds,
        Instant createdAt
) {
}
