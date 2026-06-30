package org.example.websocket.gateway.chatroom.adapter.out.stomp.payload;

import org.example.websocket.gateway.chatroom.application.service.command.MyChatRoomBadgeCommand;

import java.time.Instant;

public record StompMyChatRoomBadgePayload(
        String roomId,
        String lastMsgContent,
        Instant lastMsgCreatedAt
) {

    public static StompMyChatRoomBadgePayload from(MyChatRoomBadgeCommand command) {
        return new StompMyChatRoomBadgePayload(
                command.roomId(),
                command.lastMsgContent(),
                command.lastMsgCreatedAt()
        );
    }
}