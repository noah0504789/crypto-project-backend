package org.example.chat.chatroom.application.dto;

import jakarta.validation.constraints.Size;
import org.example.chat.chatroom.application.validation.UniqueChatRoomTitle;
import org.example.chat.chatroom.domain.model.ChatRoomCategory;
import org.example.common.validation.NotBlankIfPresent;

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
    public ChatRoomUpdateCommand toCommand() {
        return new ChatRoomUpdateCommand(title, description, category);
    }
}