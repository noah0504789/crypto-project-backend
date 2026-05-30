package event;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.example.websocket.gateway.adapter.in.event.WebSocketSessionEventHandler;
import org.example.websocket.gateway.session.application.cache.LocalSessionCache;
import org.example.websocket.gateway.session.application.out.SessionLocationPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebSocketSessionEventHandlerTest {

    @Spy
    private SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Mock
    private LocalSessionCache localSessionCache;

    @Mock
    private SessionLocationPort sessionLocationPort;

    @InjectMocks
    private WebSocketSessionEventHandler sut;

    private final String instanceId = "instance-1";
    private final String userId = "user-1";
    private final String sessionId = "session-1";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(sut, "instanceId", instanceId);
    }

    @Nested
    @DisplayName("handleConnect")
    class HandleConnectTest {

        @Test
        @DisplayName("새 WebSocket 세션이면 로컬 캐시와 Redis 위치를 저장하고 activeSessions를 증가시킨다")
        void handleConnectNewSession() {
            SessionConnectEvent event = connectEvent(sessionId, userId);

            given(localSessionCache.findUserId(sessionId))
                    .willReturn(null);

            sut.handleConnect(event);

            InOrder inOrder = inOrder(localSessionCache, sessionLocationPort);

            inOrder.verify(localSessionCache).findUserId(sessionId);
            inOrder.verify(localSessionCache).register(sessionId, userId);
            inOrder.verify(sessionLocationPort).save(userId, sessionId, instanceId);

            assertThat(activeSessionGauge()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("이미 등록된 WebSocket 세션이면 activeSessions를 증가시키지 않는다")
        void handleConnectExistingSession() {
            SessionConnectEvent event = connectEvent(sessionId, userId);

            given(localSessionCache.findUserId(sessionId))
                    .willReturn(userId);

            sut.handleConnect(event);

            verify(localSessionCache).findUserId(sessionId);
            verify(localSessionCache).register(sessionId, userId);
            verify(sessionLocationPort).save(userId, sessionId, instanceId);

            assertThat(activeSessionGauge()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("sessionId가 없으면 연결 처리를 무시한다")
        void handleConnectWithoutSessionId() {
            SessionConnectEvent event = connectEvent(null, userId);

            sut.handleConnect(event);

            verifyNoInteractions(localSessionCache, sessionLocationPort);
            assertThat(activeSessionGauge()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("userId가 없으면 연결 처리를 무시한다")
        void handleConnectWithoutUserId() {
            SessionConnectEvent event = connectEvent(sessionId, null);

            sut.handleConnect(event);

            verifyNoInteractions(localSessionCache, sessionLocationPort);
            assertThat(activeSessionGauge()).isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("handleSubscribe")
    class HandleSubscribeTest {

        @Test
        @DisplayName("구독 이벤트가 발생하면 세션의 userId를 찾아 Redis TTL을 갱신한다")
        void handleSubscribe() {
            SessionSubscribeEvent event = subscribeEvent(sessionId);

            given(localSessionCache.findUserId(sessionId))
                    .willReturn(userId);

            sut.handleSubscribe(event);

            verify(localSessionCache).findUserId(sessionId);
            verify(sessionLocationPort).refreshTtl(userId);
        }

        @Test
        @DisplayName("sessionId가 없으면 TTL 갱신을 하지 않는다")
        void handleSubscribeWithoutSessionId() {
            SessionSubscribeEvent event = subscribeEvent(null);

            sut.handleSubscribe(event);

            verifyNoInteractions(localSessionCache, sessionLocationPort);
        }

        @Test
        @DisplayName("로컬 캐시에 userId가 없으면 TTL 갱신을 하지 않는다")
        void handleSubscribeWithoutCachedUserId() {
            SessionSubscribeEvent event = subscribeEvent(sessionId);

            given(localSessionCache.findUserId(sessionId))
                    .willReturn(null);

            sut.handleSubscribe(event);

            verify(localSessionCache).findUserId(sessionId);
            verify(sessionLocationPort, never()).refreshTtl(anyString());
        }
    }

    @Nested
    @DisplayName("handleDisconnect")
    class HandleDisconnectTest {

        @Test
        @DisplayName("연결 해제 시 Redis 위치를 삭제하고 로컬 캐시를 제거하며 activeSessions를 감소시킨다")
        void handleDisconnect() {
            SessionConnectEvent connectEvent = connectEvent(sessionId, userId);
            SessionDisconnectEvent disconnectEvent = disconnectEvent(sessionId);

            given(localSessionCache.findUserId(sessionId))
                    .willReturn(null)
                    .willReturn(userId);

            sut.handleConnect(connectEvent);
            assertThat(activeSessionGauge()).isEqualTo(1.0);

            sut.handleDisconnect(disconnectEvent);

            verify(sessionLocationPort).deleteIfServerMatches(userId, sessionId, instanceId);
            verify(localSessionCache).remove(sessionId);

            assertThat(activeSessionGauge()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("연결 해제 시 sessionId가 없으면 아무 작업도 하지 않는다")
        void handleDisconnectWithoutSessionId() {
            SessionDisconnectEvent event = disconnectEvent(null);

            sut.handleDisconnect(event);

            verifyNoInteractions(localSessionCache, sessionLocationPort);
            assertThat(activeSessionGauge()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("연결 해제 시 로컬 캐시에 userId가 없으면 Redis 삭제와 로컬 제거를 하지 않는다")
        void handleDisconnectWithoutCachedUserId() {
            SessionDisconnectEvent event = disconnectEvent(sessionId);

            given(localSessionCache.findUserId(sessionId))
                    .willReturn(null);

            sut.handleDisconnect(event);

            verify(localSessionCache).findUserId(sessionId);
            verify(localSessionCache, never()).remove(anyString());
            verify(sessionLocationPort, never())
                    .deleteIfServerMatches(anyString(), anyString(), anyString());

            assertThat(activeSessionGauge()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("activeSessions가 0일 때 연결 해제가 발생해도 음수가 되지 않는다")
        void handleDisconnectDoesNotGoBelowZero() {
            SessionDisconnectEvent event = disconnectEvent(sessionId);

            given(localSessionCache.findUserId(sessionId))
                    .willReturn(userId);

            sut.handleDisconnect(event);

            verify(sessionLocationPort).deleteIfServerMatches(userId, sessionId, instanceId);
            verify(localSessionCache).remove(sessionId);

            assertThat(activeSessionGauge()).isEqualTo(0.0);
        }
    }

    private SessionConnectEvent connectEvent(String sessionId, String userId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId(sessionId);

        if (userId != null) {
            accessor.setUser(() -> userId);
        }

        Message<byte[]> message = MessageBuilder.createMessage(
                new byte[0],
                accessor.getMessageHeaders()
        );

        return new SessionConnectEvent(this, message);
    }

    private SessionSubscribeEvent subscribeEvent(String sessionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId(sessionId);

        Message<byte[]> message = MessageBuilder.createMessage(
                new byte[0],
                accessor.getMessageHeaders()
        );

        return new SessionSubscribeEvent(this, message);
    }

    private SessionDisconnectEvent disconnectEvent(String sessionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        if (sessionId != null) accessor.setSessionId(sessionId);

        Message<byte[]> message = MessageBuilder.createMessage(
                new byte[0],
                accessor.getMessageHeaders()
        );

        return new SessionDisconnectEvent(
                this,
                message,
                sessionId != null ? sessionId : "dummy-session-id",
                CloseStatus.NORMAL
        );
    }

    private double activeSessionGauge() {
        Gauge gauge = meterRegistry.find("ws_active_sessions").gauge();

        assertThat(gauge).isNotNull();

        return gauge.value();
    }
}