package org.example.websocket.gateway.chatmessage.adapter.out.stomp.payload;

import org.example.websocket.gateway.chatmessage.application.service.command.ChatMessageBroadcastCommand;

public record StompChatMessagePayload(
        String messageId,
        String roomId,
        String writerId,
        String content,
        long timestamp,
        String clientMessageId
) {

    public static StompChatMessagePayload from(ChatMessageBroadcastCommand command) {
        return new StompChatMessagePayload(
                command.messageId(),
                command.roomId(),
                command.writerId(),
                command.content(),
                command.timestamp(),
                command.clientMessageId()
        );
    }
}