package org.example.websocket.gateway.chatmessage.adapter.out.stomp;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.example.common.properties.ApiPathProperties;
import org.example.websocket.gateway.chatmessage.application.service.result.ChatMessageAckResult;
import org.example.websocket.gateway.session.application.cache.LocalSessionCache;
import org.example.websocket.gateway.websocket.adapter.out.stomp.DirectStompSessionSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.support.ExecutorSubscribableChannel;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DirectStompChatMessageAckAdapter")
class DirectStompChatMessageAckAdapterUnitTest {

    private MeterRegistry registry;
    private LocalSessionCache localSessionCache;
    private ExecutorSubscribableChannel clientOutboundChannel;
    private List<Message<?>> captured;

    private final String userId = "user-1";
    private final String sessionId = "session-1";
    private final String subscriptionId = "sub-0";
    private final ChatMessageAckResult result = new ChatMessageAckResult("msg-1", "client-1", true, 1L);

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        localSessionCache = new LocalSessionCache();
        captured = new ArrayList<>();

        clientOutboundChannel = new ExecutorSubscribableChannel();
        clientOutboundChannel.subscribe(captured::add);
    }

    private DirectStompChatMessageAckAdapter sut() {
        return new DirectStompChatMessageAckAdapter(
                localSessionCache,
                new DirectStompSessionSender(clientOutboundChannel, new MappingJackson2MessageConverter()),
                // 이 어댑터가 쓰는 것은 stomp().userDestinationPrefix() 하나뿐이다.
                new ApiPathProperties(
                        null,
                        new ApiPathProperties.Stomp("/msg", "/user"),
                        null, null, null, null, null, null, null, null, null
                ),
                registry
        );
    }

    private SimpMessageHeaderAccessor headersOf(Message<?> message) {
        return SimpMessageHeaderAccessor.wrap(message);
    }

    @Test
    @DisplayName("세션과 구독을 알면 brokerChannel 없이 직접 보낸다")
    void sendsDirectlyWhenSessionAndSubscriptionKnown() {
        // given
        localSessionCache.register(sessionId, userId);
        localSessionCache.registerAckSubscription(sessionId, subscriptionId);

        // when
        sut().success(userId, result);

        // then
        assertThat(captured).hasSize(1);
        assertThat(registry.counter("chat.message.ack.direct.sent").count()).isEqualTo(1.0);
        assertThat(registry.counter("chat.message.ack.direct.failed").count()).isZero();
    }

    @Test
    @DisplayName("클라이언트가 매칭할 수 있도록 세션·구독·목적지를 채운다")
    void fillsSessionSubscriptionAndDestination() {
        // given
        localSessionCache.register(sessionId, userId);
        localSessionCache.registerAckSubscription(sessionId, subscriptionId);

        // when
        sut().success(userId, result);

        // then
        SimpMessageHeaderAccessor accessor = headersOf(captured.get(0));
        assertThat(accessor.getSessionId()).isEqualTo(sessionId);
        assertThat(accessor.getSubscriptionId()).isEqualTo(subscriptionId);
        assertThat(accessor.getDestination()).isEqualTo("/user/queue/chat/ack");
        assertThat(captured.get(0).getPayload()).isInstanceOf(byte[].class);
    }

    @Test
    @DisplayName("구독 정보가 없으면 보내지 않는다")
    void doesNotSendWhenSubscriptionUnknown() {
        // given — 세션은 있지만 ACK 구독을 아직 안 했다
        localSessionCache.register(sessionId, userId);

        // when
        sut().success(userId, result);

        // then
        assertThat(captured).isEmpty();
        assertThat(registry.counter("chat.message.ack.direct.failed").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("로컬 세션이 없으면 보내지 않는다")
    void doesNotSendWhenNoLocalSession() {
        // when
        sut().failure(userId, "client-1", "SERVER_ERROR");

        // then
        assertThat(captured).isEmpty();
        assertThat(registry.counter("chat.message.ack.direct.failed").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("한 사용자의 세션이 여럿이면 각각 보낸다")
    void sendsToEverySessionOfUser() {
        // given
        localSessionCache.register(sessionId, userId);
        localSessionCache.register("session-2", userId);
        localSessionCache.registerAckSubscription(sessionId, subscriptionId);
        localSessionCache.registerAckSubscription("session-2", "sub-1");

        // when
        sut().success(userId, result);

        // then
        assertThat(captured).hasSize(2);
        assertThat(registry.counter("chat.message.ack.direct.sent").count()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("실패 ACK 도 같은 경로로 보낸다")
    void sendsFailureAckDirectly() {
        // given
        localSessionCache.register(sessionId, userId);
        localSessionCache.registerAckSubscription(sessionId, subscriptionId);

        // when
        sut().failure(userId, "client-1", "RATE_LIMIT_EXCEEDED");

        // then
        assertThat(captured).hasSize(1);
        assertThat(registry.counter("chat.message.ack.direct.failed").count()).isZero();
    }
}
