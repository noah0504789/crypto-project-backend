package org.example.contract.chatroom;

import java.time.Instant;
import java.util.Set;

public record MyChatRoomPayload(
        String id,
        Set<String> memberIds,
        String lastMsgContent,
        Instant lastMsgCreatedAt
) {
    public static MyChatRoomPayload ofLastMessage(String id, Set<String> memberIds, String content, Instant createdAt) {
        return new MyChatRoomPayload(id, memberIds, content, createdAt);
    }
}
