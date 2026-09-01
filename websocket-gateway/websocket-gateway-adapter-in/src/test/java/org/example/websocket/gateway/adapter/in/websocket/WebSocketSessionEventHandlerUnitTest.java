package org.example.websocket.gateway.adapter.in.websocket;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.example.common.properties.ApiPathProperties;
import org.example.websocket.gateway.session.application.cache.LocalSessionCache;
import org.example.websocket.gateway.session.application.out.SessionLocationPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebSocketSessionEventHandler")
class WebSocketSessionEventHandlerUnitTest {

    @Mock
    private SessionLocationPort sessionLocationPort;

    private LocalSessionCache localSessionCache;
    private WebSocketSessionEventHandler sut;

    private final String userId = "user-1";
    private final String sessionId = "session-1";
    private final String subscriptionId = "badge-sub-1";

    @BeforeEach
    void setUp() {
        localSessionCache = new LocalSessionCache();
        localSessionCache.register(sessionId, userId);

        sut = new WebSocketSessionEventHandler(
                new SimpleMeterRegistry(),
                localSessionCache,
                sessionLocationPort,
                new ApiPathProperties(
                        null,
                        new ApiPathProperties.Stomp("/msg", "/user"),
                        null, null, null, null, null, null, null, null, null
                )
        );
    }

    @Test
    @DisplayName("뱃지 SUBSCRIBE 이벤트에서 세션별 구독 ID를 저장한다")
    void registersBadgeSubscription() {
        // when
        sut.handleSubscribe(new SessionSubscribeEvent(
                this,
                message(StompCommand.SUBSCRIBE, "/user/queue/chat/badge")
        ));

        // then
        assertThat(localSessionCache.findBadgeSubscriptionId(sessionId)).isEqualTo(subscriptionId);
    }

    @Test
    @DisplayName("UNSUBSCRIBE 이벤트에서 뱃지 구독 ID를 제거한다")
    void removesBadgeSubscription() {
        // given
        localSessionCache.registerBadgeSubscription(sessionId, subscriptionId);

        // when
        sut.handleUnsubscribe(new SessionUnsubscribeEvent(
                this,
                message(StompCommand.UNSUBSCRIBE, null)
        ));

        // then
        assertThat(localSessionCache.findBadgeSubscriptionId(sessionId)).isNull();
    }

    private Message<byte[]> message(StompCommand command, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setSessionId(sessionId);
        accessor.setSubscriptionId(subscriptionId);

        if (destination != null) {
            accessor.setDestination(destination);
        }

        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
