package org.example.websocket.gateway.chatmessage.application.port.out;

import org.example.websocket.gateway.chatmessage.application.service.command.ChatMessageBroadcastCommand;

public interface ChatMessageBroadcastPort {

    boolean broadcast(ChatMessageBroadcastCommand command, String txId);
}