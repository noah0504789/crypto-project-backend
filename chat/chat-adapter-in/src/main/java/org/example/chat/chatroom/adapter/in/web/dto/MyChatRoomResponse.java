package org.example.chat.chatroom.adapter.in.web.dto;

import lombok.Builder;
import org.example.chat.chatroom.domain.model.ChatRoomCategory;
import org.example.chat.chatroom.application.service.result.MyChatRoomSummary;

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
