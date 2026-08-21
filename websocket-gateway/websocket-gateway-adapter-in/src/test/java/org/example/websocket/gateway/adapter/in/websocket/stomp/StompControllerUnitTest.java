package org.example.websocket.gateway.adapter.in.websocket.stomp;

import org.example.websocket.gateway.adapter.in.websocket.stomp.dto.StompChatMessageSendRequest;
import org.example.websocket.gateway.adapter.in.websocket.stomp.mapper.StompChatMessageMapper;
import org.example.websocket.gateway.adapter.in.websocket.stomp.ratelimit.ChatMessageRateLimitExceededException;
import org.example.websocket.gateway.adapter.in.websocket.stomp.ratelimit.RedisChatMessageRateLimiter;
import org.example.websocket.gateway.chatmessage.application.port.in.ChatMessageSendUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class StompControllerUnitTest {

    @Mock
    private ChatMessageSendUseCase chatMessageSendUseCase;

    @Mock
    private StompChatMessageMapper mapper;

    @Mock
    private RedisChatMessageRateLimiter rateLimiter;

    private StompController controller;

    @BeforeEach
    void setUp() {
        controller = new StompController(chatMessageSendUseCase, mapper, rateLimiter);
    }

    @Test
    @DisplayName("메시지 Bucket이 허용하면 Chat 전송 UseCase를 호출한다")
    void chatMessage_shouldSendWhenRateLimitAllows() {
        StompChatMessageSendRequest request = request();
        Principal principal = () -> "user-1";
        given(rateLimiter.isAllowed("user-1", "room-1")).willReturn(true);
        given(mapper.toCommand(request)).willReturn(null);

        controller.chatMessage(request, principal);

        verify(chatMessageSendUseCase).send(null);
    }

    @Test
    @DisplayName("메시지 Bucket이 거부하면 UseCase 호출 전에 Rate Limit 예외를 발생시킨다")
    void chatMessage_shouldRejectBeforeUseCase() {
        StompChatMessageSendRequest request = request();
        Principal principal = () -> "user-1";
        given(rateLimiter.isAllowed("user-1", "room-1")).willReturn(false);

        assertThatThrownBy(() -> controller.chatMessage(request, principal))
                .isInstanceOf(ChatMessageRateLimitExceededException.class);

        verifyNoInteractions(mapper, chatMessageSendUseCase);
    }

    private StompChatMessageSendRequest request() {
        return new StompChatMessageSendRequest("client-message-1", "room-1", "user-1", "hello");
    }
}
