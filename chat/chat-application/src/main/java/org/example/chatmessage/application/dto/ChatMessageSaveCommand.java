package org.example.chatmessage.application.dto;

public record ChatMessageSaveCommand(
        String messageId,
        String roomId,
        String writerId,
        String content,
        String clientMessageId
) {
}