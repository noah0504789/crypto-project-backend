package org.example.chat.chatroom.adapter.in.web.dto;

import lombok.Builder;
import org.example.chat.chatroom.domain.model.ChatRoom;
import org.example.chat.chatroom.domain.model.ChatRoomCategory;

import java.time.Instant;

@Builder
public record ChatRoomResponse(
        String id,
        String hostId,
        String title,
        String description,
        ChatRoomCategory category,
        Long msgCnt,
        Integer memberCnt,
        Double popularity,
        Instant createdAt
) {
    public static ChatRoomResponse fromDomain(ChatRoom entity) {
        return ChatRoomResponse.builder()
                .id(entity.getId())
                .hostId(entity.getHostId())
                .title(entity.getTitle())
                .description(entity.getDescription())
                .category(entity.getCategory())
                .memberCnt(entity.getMemberIds().size())
                .msgCnt(entity.getMsgCnt())
                .popularity(entity.getPopularity())
                .createdAt(entity.toInstant())
                .build();
    }
}
