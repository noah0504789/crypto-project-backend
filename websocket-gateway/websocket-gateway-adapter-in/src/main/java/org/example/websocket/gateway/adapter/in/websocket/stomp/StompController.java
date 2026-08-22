package org.example.websocket.gateway.adapter.in.websocket.stomp;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.websocket.gateway.adapter.in.websocket.stomp.mapper.StompChatMessageMapper;
import org.example.websocket.gateway.chatmessage.application.port.in.ChatMessageSendUseCase;
import org.example.websocket.gateway.adapter.in.websocket.stomp.dto.StompChatMessageSendRequest;
import org.example.websocket.gateway.adapter.in.websocket.stomp.ratelimit.ChatMessageRateLimitExceededException;
import org.example.websocket.gateway.adapter.in.websocket.stomp.ratelimit.RedisChatMessageRateLimiter;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class StompController {

    private final ChatMessageSendUseCase chatMessageSendUseCase;
    private final StompChatMessageMapper stompChatMessageMapper;
    private final RedisChatMessageRateLimiter rateLimiter;

    @MessageMapping("/chat.send")
    public void chatMessage(@Payload @Valid StompChatMessageSendRequest request, Principal principal) {
        if (!rateLimiter.isAllowed(principal.getName(), request.roomId())) {
            throw new ChatMessageRateLimitExceededException(request.clientMessageId());
        }

        chatMessageSendUseCase.send(stompChatMessageMapper.toCommand(request));
    }
}
