package org.example.chat.chatmessage.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.common.time.ServiceTimeConverter;

import java.time.Instant;
import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChatMessage {

    private String id;
    private String roomId;
    private String writerId;
    private String content;
    private LocalDateTime createdAt;

    public static ChatMessage create(String id, String roomId, String writerId, String content) {
        return ChatMessage.builder()
                .id(id)
                .roomId(roomId)
                .writerId(writerId)
                .content(content)
                .createdAt(LocalDateTime.now())
                .build();
    }

    public static ChatMessage rehydrate(String id, String roomId, String writerId, String content, Instant createdAt) {
        return ChatMessage.builder()
                .id(id)
                .roomId(roomId)
                .writerId(writerId)
                .content(content)
                .createdAt(ServiceTimeConverter.toLocalDateTime(createdAt))
                .build();
    }

    public Instant createdAtInstant() {
        return ServiceTimeConverter.toInstant(createdAt);
    }

    public long toEpochMillis() {
        return createdAtInstant().toEpochMilli();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (this.getClass() != obj.getClass()) return false;

        ChatMessage that = (ChatMessage) obj;

        return this.getId().equals(that.getId());
    }
}
