package org.example.chat.chatmessage.application.dto;

import org.example.chat.chatmessage.domain.model.ChatMessage;

public record ChatMessageSaveResult(
        String id,
        long ts
) {

    public static ChatMessageSaveResult from(ChatMessage message) {
        return new ChatMessageSaveResult(
                message.getId(),
                message.toEpochMillis()
        );
    }
}