package org.example.chatroom.adapter.in.dto;

import lombok.Builder;
import org.example.chatroom.domain.model.ChatRoomCategory;
import org.example.chatroom.application.query.MyChatRoomSummary;

import java.time.Instant;

@Builder
public record MyChatRoomResponse(
        String id,
        String hostId,
        String title,
        String description,
        ChatRoomCategory category,
        Integer memberCnt,
        String lastMsgContent,
        Instant lastMsgCreatedAt,
        Long unreadMsgCnt
) {
    public static MyChatRoomResponse from(MyChatRoomSummary summary) {
        return MyChatRoomResponse.builder()
                .id(summary.id())
                .hostId(summary.hostId())
                .title(summary.title())
                .description(summary.description())
                .category(summary.category())
                .memberCnt(summary.memberCnt())
                .lastMsgContent(summary.lastMsgContent())
                .lastMsgCreatedAt(summary.lastMsgCreatedAt())
                .unreadMsgCnt(summary.unreadMsgCnt())
                .build();
    }
}
