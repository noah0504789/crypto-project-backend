package org.example.chat.chatroom.application.service.result;

import lombok.Builder;
import org.example.chat.chatroom.domain.model.ChatRoom;
import org.example.chat.chatroom.domain.model.ChatRoomCategory;

import java.time.Instant;

@Builder
public record MyChatRoomSummary(
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
    public static MyChatRoomSummary fromRoom(ChatRoom chatRoom, Long lastMsgSeq) {
        long safeLastMsgSeq = lastMsgSeq == null ? 0L : lastMsgSeq;
        long msgCnt = chatRoom.getMsgCnt() == null ? 0L : chatRoom.getMsgCnt();

        return MyChatRoomSummary.builder()
                .id(chatRoom.getId())
                .hostId(chatRoom.getHostId())
                .title(chatRoom.getTitle())
                .description(chatRoom.getDescription())
                .category(chatRoom.getCategory())
                .memberCnt(chatRoom.getMemberIds() == null ? 0 : chatRoom.getMemberIds().size())
                .lastMsgContent(chatRoom.getLastMsgContent())
                .lastMsgCreatedAt(chatRoom.getLastMsgCreatedAt())
                .unreadMsgCnt(Math.max(0, msgCnt - safeLastMsgSeq))
                .build();
    }
}