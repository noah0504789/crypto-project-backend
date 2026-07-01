package org.example.websocket.gateway.adapter.in.websocket.stomp.mapper;

import lombok.RequiredArgsConstructor;
import org.example.websocket.gateway.chatmessage.application.service.command.ChatMessageSendCommand;
import org.example.websocket.gateway.chatmessage.application.port.out.MessageIdGeneratePort;
import org.example.websocket.gateway.stomp.dto.ChatMessageRequest;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StompChatMessageMapper {

    private final MessageIdGeneratePort messageIdGeneratePort;

    public ChatMessageSendCommand toCommand(ChatMessageRequest request) {
        return new ChatMessageSendCommand(
                request.clientMessageId(),
                messageIdGeneratePort.generate(),
                request.roomId(),
                request.writerId(),
                request.content()
        );
    }
}