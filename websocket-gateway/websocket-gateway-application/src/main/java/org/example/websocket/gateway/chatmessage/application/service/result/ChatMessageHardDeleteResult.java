package org.example.websocket.gateway.chatmessage.application.service.result;

public record ChatMessageHardDeleteResult(
        boolean success,
        String messageId,
        boolean deleted,
        boolean alreadyDeleted,
        boolean notFound
) {
}