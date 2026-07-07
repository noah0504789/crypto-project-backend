package org.example.chat.chatmessage.adapter.in.web.dto;

import lombok.Builder;
import org.example.chat.chatmessage.domain.model.ChatMessage;

import java.time.Instant;

@Builder
public record ChatMessageResponse(
        String id,
        String roomId,
        String writerId,
        String content,
        Instant createdAt
) {
    public static ChatMessageResponse fromDomain(ChatMessage entity) {
        return ChatMessageResponse.builder()
                .id(entity.getId())
                .roomId(entity.getRoomId())
                .writerId(entity.getWriterId())
                .content(entity.getContent())
                .createdAt(entity.createdAtInstant())
                .build();
    }
}
