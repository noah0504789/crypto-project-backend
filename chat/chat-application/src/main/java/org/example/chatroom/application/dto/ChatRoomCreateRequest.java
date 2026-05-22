package org.example.chatroom.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.example.chatroom.domain.model.ChatRoomCategory;
import org.example.chatroom.application.validation.UniqueChatRoomTitle;

public record ChatRoomCreateRequest(

        @UniqueChatRoomTitle
        @NotBlank
        @Size(max = 100)
        String title,

        @NotBlank
        @Size(max = 2000)
        String description,

        @NotNull
        ChatRoomCategory category
) {
}