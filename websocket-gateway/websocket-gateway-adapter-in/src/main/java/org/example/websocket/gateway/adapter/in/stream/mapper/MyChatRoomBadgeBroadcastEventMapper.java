package org.example.websocket.gateway.adapter.in.stream.mapper;

import org.example.contract.chatroom.MyChatRoomBadgePayload;
import org.example.contract.chatroom.MyChatRoomBadgeBroadcastEvent;
import org.example.websocket.gateway.chatroom.application.service.command.MyChatRoomBadgeCommand;
import org.springframework.stereotype.Component;

@Component
public class MyChatRoomBadgeBroadcastEventMapper {

    public MyChatRoomBadgeCommand toCommand(MyChatRoomBadgeBroadcastEvent event) {
        MyChatRoomBadgePayload payload = event.getPayload();

        return new MyChatRoomBadgeCommand(
                payload.id(),
                payload.memberIds(),
                payload.lastMsgContent(),
                payload.lastMsgCreatedAt()
        );
    }
}
