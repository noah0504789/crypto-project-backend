package org.example.websocket.gateway.chatmessage.application.port.in;

import org.example.websocket.gateway.chatmessage.application.service.command.ChatMessageBroadcastCommand;

public interface ChatMessageBroadcastUseCase {

    void broadcast(ChatMessageBroadcastCommand command, String txId);
}