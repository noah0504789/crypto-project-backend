package org.example.chat.chatmessage.application.service.result;

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