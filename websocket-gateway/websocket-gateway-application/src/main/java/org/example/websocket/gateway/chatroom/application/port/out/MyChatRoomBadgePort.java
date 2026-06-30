package org.example.websocket.gateway.chatroom.application.port.out;

import org.example.websocket.gateway.chatroom.application.service.command.MyChatRoomBadgeCommand;

public interface MyChatRoomBadgePort {

    boolean send(MyChatRoomBadgeCommand command, String txId);
}