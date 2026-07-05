package org.example.chat.chatroom.application.event.payload;

import org.example.chat.chatroom.domain.model.ChatRoomCategory;

import java.util.HashMap;
import java.util.Map;

public record ChatRoomUpdatedPayload(
        String title,
        String description,
        ChatRoomCategory category
) {

    public Map<String, Object> toUpdateMap() {
        Map<String, Object> map = new HashMap<>();

        if (title != null) map.put("title", title);
        if (description != null) map.put("description", description);
        if (category != null) map.put("category", category);

        return map;
    }
}