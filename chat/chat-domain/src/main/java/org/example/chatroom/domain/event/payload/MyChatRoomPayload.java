package org.example.chatroom.domain.event.payload;

import lombok.Builder;

import java.time.Instant;
import java.util.Set;

@Builder
public record MyChatRoomPayload(
        String id,
//        String hostId,
//        String title,
//        String description,
//        ChatRoomCategory category,
//        Long messageCnt,
//        Double popularity,
//        Instant createdAt,
        Set<String> memberIds,
        String lastMsgContent,
        Instant lastMsgCreatedAt
) {
    public static MyChatRoomPayload ofLastMessage(String id, Set<String> memberIds, String content, Instant createdAt) {
        return MyChatRoomPayload.builder()
                .id(id)
                .memberIds(memberIds)
                .lastMsgContent(content)
                .lastMsgCreatedAt(createdAt)
                .build();
//                .hostId(entity.getHostId())
//                .title(entity.getTitle())
//                .description(entity.getDescription())
//                .category(entity.getCategory())
//                .messageCnt(entity.getMsgCnt())
//                .popularity(entity.getPopularity())
//                .createdAt(entity.toInstant())
    }

//    public LocalDateTime toLocalDateTime() {
//        return LocalDateTime.ofInstant(createdAt, ZoneId.systemDefault());
//    }
}
