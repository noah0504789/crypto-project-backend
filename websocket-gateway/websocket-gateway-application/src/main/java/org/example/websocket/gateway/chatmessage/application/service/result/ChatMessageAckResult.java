package org.example.websocket.gateway.chatmessage.application.service.result;

import org.example.websocket.gateway.chatmessage.application.service.command.ChatMessageSendCommand;

public record ChatMessageAckResult(
        String messageId,
        String clientMessageId,
        boolean success,
        long timestamp
) {

    public static ChatMessageAckResult from(ChatMessageSendCommand command, ChatMessageSendResult result) {
        return new ChatMessageAckResult(
                result.messageId(),
                command.clientMessageId(),
                result.success(),
                result.timestamp()
        );
    }
}