package org.example.websocket.gateway.chatroom.application.service.command;

import java.time.Instant;
import java.util.Set;

public record MyChatRoomBadgeCommand(
        String roomId,
        Set<String> memberIds,
        String lastMsgContent,
        Instant lastMsgCreatedAt
) {
}