package org.example.websocket.adapter.in.event.chatroom.dto;

import org.example.contract.chatroom.MyChatRoomPayload;

public record MyChatRoomResponse(
        String id,
        String lastMsgContent,
        long lastMsgCreatedAt
) {
    public static MyChatRoomResponse fromPayload(MyChatRoomPayload payload) {
        return new MyChatRoomResponse(
                payload.id(),
                payload.lastMsgContent(),
                payload.lastMsgCreatedAt().toEpochMilli()
        );
    }
}
