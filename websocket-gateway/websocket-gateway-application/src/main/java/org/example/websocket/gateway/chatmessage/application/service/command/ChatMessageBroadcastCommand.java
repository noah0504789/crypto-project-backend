package org.example.websocket.gateway.chatmessage.application.service.command;

public record ChatMessageBroadcastCommand(
        String messageId,
        String roomId,
        String writerId,
        String content,
        long timestamp,
        String clientMessageId
) {
}
