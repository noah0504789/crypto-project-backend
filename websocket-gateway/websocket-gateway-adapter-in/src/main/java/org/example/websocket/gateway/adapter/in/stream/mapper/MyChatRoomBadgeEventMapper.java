package org.example.websocket.gateway.adapter.in.stream.mapper;

import org.example.contract.chatroom.MyChatRoomBadgePayload;
import org.example.contract.chatroom.MyChatRoomBadgeEvent;
import org.example.websocket.gateway.chatroom.application.service.command.MyChatRoomBadgeCommand;
import org.springframework.stereotype.Component;

@Component
public class MyChatRoomBadgeEventMapper {

    public MyChatRoomBadgeCommand toCommand(MyChatRoomBadgeEvent event) {
        MyChatRoomBadgePayload payload = event.getPayload();

        return new MyChatRoomBadgeCommand(
                payload.id(),
                payload.memberIds(),
                payload.lastMsgContent(),
                payload.lastMsgCreatedAt()
        );
    }
}