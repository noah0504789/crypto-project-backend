package org.example.websocket.gateway.chatroom.application.port.in;

import org.example.websocket.gateway.chatroom.application.service.command.MyChatRoomBadgeCommand;

public interface MyChatRoomBadgeEventHandler {

    void handle(MyChatRoomBadgeCommand command, String txId);
}