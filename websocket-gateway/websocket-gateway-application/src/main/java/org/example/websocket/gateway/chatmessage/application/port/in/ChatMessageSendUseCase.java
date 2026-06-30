package org.example.websocket.gateway.chatmessage.application.port.in;

import org.example.websocket.gateway.chatmessage.application.service.command.ChatMessageSendCommand;

public interface ChatMessageSendUseCase {

    void send(ChatMessageSendCommand command);
}