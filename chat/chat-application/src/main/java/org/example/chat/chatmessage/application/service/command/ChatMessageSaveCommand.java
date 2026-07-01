package org.example.chat.chatmessage.application.service.command;

public record ChatMessageSaveCommand(
        String messageId,
        String roomId,
        String writerId,
        String content,
        String clientMessageId
) {
}