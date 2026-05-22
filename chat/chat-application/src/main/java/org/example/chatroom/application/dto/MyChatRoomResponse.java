package org.example.chatroom.application.dto;

import lombok.Builder;
import org.example.chatroom.domain.model.ChatRoom;
import org.example.chatroom.domain.model.ChatRoomCategory;

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
    public static MyChatRoomResponse fromRoom(ChatRoom chatRoom, Long lastMsgSeq) {
        return MyChatRoomResponse.builder()
                .id(chatRoom.getId())
                .hostId(chatRoom.getHostId())
                .title(chatRoom.getTitle())
                .memberCnt(chatRoom.getMemberIds().size())
                .lastMsgContent(chatRoom.getLastMsgContent())
                .lastMsgCreatedAt(chatRoom.getLastMsgCreatedAt())
                .unreadMsgCnt(chatRoom.getMsgCnt() - lastMsgSeq)
                .description(chatRoom.getDescription())
                .category(chatRoom.getCategory())
                .build();
    }
}
