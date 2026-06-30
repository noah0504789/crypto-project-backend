package org.example.websocket.gateway.adapter.in.event.chatroom;

import org.example.contract.chatroom.MyChatRoomPayload;
import org.example.contract.chatroom.MyChatRoomBadgeEvent;
import org.example.websocket.gateway.chatroom.application.service.command.MyChatRoomBadgeCommand;
import org.springframework.stereotype.Component;

@Component
public class MyChatRoomBadgeEventMapper {

    public MyChatRoomBadgeCommand toCommand(MyChatRoomBadgeEvent event) {
        MyChatRoomPayload payload = event.payload();

        return new MyChatRoomBadgeCommand(
                payload.id(),
                payload.memberIds(),
                payload.lastMsgContent(),
                payload.lastMsgCreatedAt()
        );
    }
}