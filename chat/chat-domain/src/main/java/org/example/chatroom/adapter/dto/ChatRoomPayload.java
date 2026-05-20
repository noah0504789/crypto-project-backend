package org.example.chatroom.adapter.dto;

import lombok.Builder;
import org.example.chatroom.domain.model.ChatRoom;
import org.example.chatroom.domain.model.ChatRoomCategory;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;

@Builder
public record ChatRoomPayload(
        String id,
        String hostId,
        String title,
        String description,
        ChatRoomCategory category,
//        Long messageCnt,
//        Double popularity,
        Set<String> memberIds,
        Instant createdAt
) {
    public static ChatRoomPayload fromDomain(ChatRoom entity) {
        return ChatRoomPayload.builder()
                .id(entity.getId())
                .hostId(entity.getHostId())
                .title(entity.getTitle())
                .description(entity.getDescription())
                .category(entity.getCategory())
//                .messageCnt(entity.getMsgCnt())
//                .popularity(entity.getPopularity())
                .memberIds(entity.getMemberIds())
                .createdAt(entity.toInstant())
                .build();
    }

    public LocalDateTime toLocalDateTime() {
        return LocalDateTime.ofInstant(createdAt, ZoneId.systemDefault());
    }
}
