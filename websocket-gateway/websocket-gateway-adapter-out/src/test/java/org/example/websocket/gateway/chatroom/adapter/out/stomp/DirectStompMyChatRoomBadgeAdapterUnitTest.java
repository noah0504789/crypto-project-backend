package org.example.websocket.gateway.chatroom.adapter.out.stomp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.example.common.properties.ApiPathProperties;
import org.example.websocket.gateway.chatroom.application.service.command.MyChatRoomBadgeCommand;
import org.example.websocket.gateway.session.application.cache.LocalSessionCache;
import org.example.websocket.gateway.websocket.adapter.out.stomp.DirectStompSessionSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.support.ExecutorSubscribableChannel;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("DirectStompMyChatRoomBadgeAdapter")
class DirectStompMyChatRoomBadgeAdapterUnitTest {

    private MeterRegistry registry;
    private LocalSessionCache localSessionCache;
    private ExecutorSubscribableChannel clientOutboundChannel;
    private List<Message<?>> captured;

    private final String userId = "user-1";
    private final String sessionId = "session-1";
    private final String subscriptionId = "badge-sub-1";
    private final MyChatRoomBadgeCommand command = new MyChatRoomBadgeCommand(
            "room-1",
            Set.of(userId),
            "새 메시지",
            Instant.parse("2026-09-01T00:00:00Z")
    );

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        localSessionCache = new LocalSessionCache();
        captured = new ArrayList<>();

        clientOutboundChannel = new ExecutorSubscribableChannel();
        clientOutboundChannel.subscribe(captured::add);
    }

    private DirectStompMyChatRoomBadgeAdapter sut() {
        MappingJackson2MessageConverter messageConverter = new MappingJackson2MessageConverter();
        messageConverter.setObjectMapper(new ObjectMapper().findAndRegisterModules());

        return sut(new DirectStompSessionSender(clientOutboundChannel, messageConverter));
    }

    private DirectStompMyChatRoomBadgeAdapter sut(DirectStompSessionSender sessionSender) {
        return new DirectStompMyChatRoomBadgeAdapter(
                localSessionCache,
                sessionSender,
                new ApiPathProperties(
                        null,
                        new ApiPathProperties.Stomp("/msg", "/user"),
                        null, null, null, null, null, null, null, null, null
                ),
                registry
        );
    }

    @Test
    @DisplayName("세션과 뱃지 구독을 알면 brokerChannel 없이 직접 보낸다")
    void sendsDirectlyWhenSessionAndSubscriptionKnown() {
        // given
        localSessionCache.register(sessionId, userId);
        localSessionCache.registerBadgeSubscription(sessionId, subscriptionId);

        // when
        boolean sent = sut().send(command, "tx-1");

        // then
        assertThat(sent).isTrue();
        assertThat(captured).hasSize(1);
        assertThat(registry.counter("chat.badge.direct.sent").count()).isEqualTo(1.0);
        assertThat(registry.counter("chat.badge.direct.skipped").count()).isZero();
        assertThat(registry.counter("chat.badge.direct.failed").count()).isZero();
    }

    @Test
    @DisplayName("클라이언트가 매칭할 수 있도록 세션·구독·목적지를 채운다")
    void fillsSessionSubscriptionAndDestination() {
        // given
        localSessionCache.register(sessionId, userId);
        localSessionCache.registerBadgeSubscription(sessionId, subscriptionId);

        // when
        sut().send(command, "tx-1");

        // then
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.wrap(captured.get(0));
        assertThat(accessor.getSessionId()).isEqualTo(sessionId);
        assertThat(accessor.getSubscriptionId()).isEqualTo(subscriptionId);
        assertThat(accessor.getDestination()).isEqualTo("/user/queue/chat/badge");
        assertThat(captured.get(0).getPayload()).isInstanceOf(byte[].class);
    }

    @Test
    @DisplayName("뱃지 구독 정보가 없으면 보내지 않는다")
    void doesNotSendWhenSubscriptionUnknown() {
        // given
        localSessionCache.register(sessionId, userId);

        // when
        boolean sent = sut().send(command, "tx-1");

        // then
        assertThat(sent).isFalse();
        assertThat(captured).isEmpty();
        assertThat(registry.counter("chat.badge.direct.skipped").count()).isZero();
        assertThat(registry.counter("chat.badge.direct.failed").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("outbound 전송이 실패하면 direct failed로 센다")
    void countsOutboundFailure() {
        // given
        DirectStompSessionSender sessionSender = mock(DirectStompSessionSender.class);
        when(sessionSender.send(anyString(), anyString(), anyString(), any())).thenReturn(false);
        localSessionCache.register(sessionId, userId);
        localSessionCache.registerBadgeSubscription(sessionId, subscriptionId);

        // when
        boolean sent = sut(sessionSender).send(command, "tx-1");

        // then
        assertThat(sent).isFalse();
        assertThat(registry.counter("chat.badge.direct.sent").count()).isZero();
        assertThat(registry.counter("chat.badge.direct.skipped").count()).isZero();
        assertThat(registry.counter("chat.badge.direct.failed").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("로컬 세션이 없으면 실패가 아니라 정상 skip으로 센다")
    void skipsWhenNoLocalSession() {
        // when
        boolean sent = sut().send(command, "tx-1");

        // then
        assertThat(sent).isFalse();
        assertThat(captured).isEmpty();
        assertThat(registry.counter("chat.badge.direct.skipped").count()).isEqualTo(1.0);
        assertThat(registry.counter("chat.badge.direct.failed").count()).isZero();
    }

    @Test
    @DisplayName("한 사용자의 세션이 여럿이면 각각 직접 보낸다")
    void sendsToEverySessionOfUser() {
        // given
        localSessionCache.register(sessionId, userId);
        localSessionCache.register("session-2", userId);
        localSessionCache.registerBadgeSubscription(sessionId, subscriptionId);
        localSessionCache.registerBadgeSubscription("session-2", "badge-sub-2");

        // when
        sut().send(command, "tx-1");

        // then
        assertThat(captured).hasSize(2);
        assertThat(registry.counter("chat.badge.direct.sent").count()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("여러 세션 중 하나라도 구독 정보가 없으면 보내지 않는다")
    void doesNotSendWhenAnySubscriptionUnknown() {
        // given
        localSessionCache.register(sessionId, userId);
        localSessionCache.register("session-2", userId);
        localSessionCache.registerBadgeSubscription(sessionId, subscriptionId);

        // when
        sut().send(command, "tx-1");

        // then
        assertThat(captured).isEmpty();
        assertThat(registry.counter("chat.badge.direct.sent").count()).isZero();
        assertThat(registry.counter("chat.badge.direct.skipped").count()).isZero();
        assertThat(registry.counter("chat.badge.direct.failed").count()).isEqualTo(1.0);
    }

}
