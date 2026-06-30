package org.example.websocket.gateway.chatmessage.application.service.result;

public record ChatMessageSendResult(
        boolean success,
        String messageId,
        long timestamp
) {
}