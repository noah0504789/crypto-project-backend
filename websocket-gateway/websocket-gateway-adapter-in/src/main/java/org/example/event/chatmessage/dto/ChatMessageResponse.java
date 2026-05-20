package org.example.event.chatmessage.dto;

import org.example.contract.chatmessage.ChatMessagePayload;

public record ChatMessageResponse(
        String clientMessageId,
        String id,
        String writerId,
        String content,
        long createdAt
) {
    public static ChatMessageResponse fromPayload(ChatMessagePayload payload, String clientMessageId) {
        return new ChatMessageResponse(
                clientMessageId,
                payload.id(),
                payload.writerId(),
                payload.content(),
                payload.createdAt().toEpochMilli()
        );
    }
}
