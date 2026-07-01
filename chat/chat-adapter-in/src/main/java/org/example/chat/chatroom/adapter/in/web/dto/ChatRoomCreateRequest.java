package org.example.chat.chatroom.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.example.chat.chatroom.application.service.command.ChatRoomCreateCommand;
import org.example.chat.chatroom.domain.model.ChatRoomCategory;
import org.example.chat.chatroom.application.validation.UniqueChatRoomTitle;

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

    public ChatRoomCreateCommand toCommand(String hostId) {
        return new ChatRoomCreateCommand(hostId, title, description, category);
    }
}