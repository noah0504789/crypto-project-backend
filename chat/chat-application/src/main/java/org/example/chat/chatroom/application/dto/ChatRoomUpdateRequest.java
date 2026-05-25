package org.example.chat.chatroom.application.dto;

import jakarta.validation.constraints.Size;
import org.example.chat.chatroom.domain.model.ChatRoomCategory;
import org.example.common.validation.NotBlankIfPresent;
import org.example.chat.chatroom.application.validation.UniqueChatRoomTitle;

import java.util.HashMap;
import java.util.Map;

public record ChatRoomUpdateRequest(

    @UniqueChatRoomTitle
    @NotBlankIfPresent
    @Size(max = 100)
    String title,

    @NotBlankIfPresent
    @Size(max = 2000)
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
