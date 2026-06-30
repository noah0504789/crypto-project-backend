package org.example.websocket.gateway.chatmessage.application.port.in;

import org.example.websocket.gateway.chatmessage.application.service.command.ChatMessageBroadcastCommand;

public interface ChatMessageBroadcastEventHandler {

    void handle(ChatMessageBroadcastCommand command, String txId);
}