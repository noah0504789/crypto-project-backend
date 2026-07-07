package org.example.contract.chatroom;

import java.time.Instant;
import java.util.Set;

public record MyChatRoomBadgePayload(
        String id,
        Set<String> memberIds,
        String lastMsgContent,
        Instant lastMsgCreatedAt
) {

    public static MyChatRoomBadgePayload ofLastMessage(String id, Set<String> memberIds, String content, Instant createdAt) {
        return new MyChatRoomBadgePayload(id, memberIds, content, createdAt);
    }
}
