package org.example.chatmessage.domain.event.payload;

import lombok.Builder;
import org.example.chatmessage.domain.model.ChatMessage;

import java.time.Instant;

@Builder
public record ChatMessagePayload(
        String id,
        String roomId,
        String writerId,
        String content,
        Instant createdAt
) {
    public static ChatMessagePayload fromDomain(ChatMessage entity) {
        return ChatMessagePayload.builder()
                .id(entity.getId())
                .roomId(entity.getRoomId())
                .writerId(entity.getWriterId())
                .content(entity.getContent())
                .createdAt(entity.toInstant())
                .build();
    }
}
