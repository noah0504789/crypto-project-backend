package org.example.websocket.gateway.adapter.in.websocket.stomp;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.websocket.gateway.adapter.in.websocket.stomp.mapper.StompChatMessageMapper;
import org.example.websocket.gateway.chatmessage.application.port.in.ChatMessageSendUseCase;
import org.example.websocket.gateway.stomp.dto.ChatMessageRequest;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class StompController {

    private final ChatMessageSendUseCase chatMessageSendUseCase;
    private final StompChatMessageMapper stompChatMessageMapper;

    @MessageMapping("/chat.send")
    public void chatMessage(@Payload @Valid ChatMessageRequest request) {
        chatMessageSendUseCase.send(stompChatMessageMapper.toCommand(request));
    }
}