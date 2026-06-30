package org.example.websocket.gateway.chatmessage.application.service.command;

import java.util.Set;

public record ChatMessageBroadcastCommand(
        String messageId,
        String roomId,
        String writerId,
        String content,
        long timestamp,
        Set<String> memberIds,
        String clientMessageId
) {
}