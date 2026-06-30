package org.example.websocket.gateway.chatmessage.application.service.command;

public record ChatMessageSendCommand(
        String clientMessageId,
        String messageId,
        String roomId,
        String writerId,
        String content
) {
}