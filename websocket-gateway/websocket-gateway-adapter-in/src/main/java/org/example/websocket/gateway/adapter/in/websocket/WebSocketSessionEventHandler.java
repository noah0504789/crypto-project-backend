package org.example.websocket.gateway.adapter.in.websocket;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.example.common.enums.StompDestination;
import org.example.common.properties.ApiPathProperties;
import org.example.websocket.gateway.session.application.cache.LocalSessionCache;
import org.example.websocket.gateway.session.application.out.SessionLocationPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class WebSocketSessionEventHandler {

    @Value("${app.instance-id}")
    private String instanceId;

    private final AtomicInteger activeSessions = new AtomicInteger(0);
    private final LocalSessionCache localSessionCache;
    private final SessionLocationPort sessionLocationPort;
    private final String ackDestination;
    private final String badgeDestination;

    public WebSocketSessionEventHandler(
            MeterRegistry registry,
            LocalSessionCache localSessionCache,
            SessionLocationPort sessionLocationPort,
            ApiPathProperties apiPathProperties
    ) {
        Gauge.builder("ws_active_sessions", activeSessions, AtomicInteger::get)
                .description("Current active WebSocket sessions")
                .register(registry);

        // 축출 없는 맵이라 크기를 노출한다. ws_active_sessions 와 벌어지면 세션 정리가 안 되고 있다는 신호다.
        Gauge.builder("ws_local_sessions", localSessionCache, LocalSessionCache::sessionCount)
                .description("Sessions tracked by this instance")
                .register(registry);

        Gauge.builder("ws_local_subscribed_rooms", localSessionCache, LocalSessionCache::subscribedRoomCount)
                .description("Chat rooms with at least one local subscriber")
                .register(registry);

        this.localSessionCache = localSessionCache;
        this.sessionLocationPort = sessionLocationPort;
        this.ackDestination = apiPathProperties.stomp().userDestinationPrefix()
                + StompDestination.CHAT_ACK_QUEUE.destination();
        this.badgeDestination = apiPathProperties.stomp().userDestinationPrefix()
                + StompDestination.CHAT_ROOM_BADGE_QUEUE.destination();
    }

    @EventListener
    public void handleConnect(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());

        String sessionId = accessor.getSessionId();
        String userId = accessor.getUser() != null ? accessor.getUser().getName() : null;

        if (sessionId == null || userId == null) {
            log.warn("[ws] connect ignored. instance-index={}, userId={}, sessionId={}", instanceId, userId, sessionId);
            return;
        }

        boolean isNewSession = localSessionCache.findUserId(sessionId) == null;

        localSessionCache.register(sessionId, userId);
        sessionLocationPort.save(userId, sessionId, instanceId);

        if (isNewSession) {
            activeSessions.incrementAndGet();
        }

        log.debug("[ws] connected instance-index={}, userId={}, sessionId={}, newSession={}, activeSessions={}", instanceId, userId, sessionId, isNewSession, activeSessions.get());
    }

    @EventListener
    public void handleSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());

        String sessionId = accessor.getSessionId();
        if (sessionId == null) return;

        String userId = localSessionCache.findUserId(sessionId);
        if (userId == null) return;

        sessionLocationPort.refreshTtl(userId);

        String destination = accessor.getDestination();
        String subscriptionId = accessor.getSubscriptionId();

        if (subscriptionId != null) {
            // ACK 를 brokerChannel 없이 직접 보내려면 구독 ID 가 필요하다. 여기서만 얻을 수 있다.
            if (ackDestination.equals(destination)) {
                localSessionCache.registerAckSubscription(sessionId, subscriptionId);
            }

            if (badgeDestination.equals(destination)) {
                localSessionCache.registerBadgeSubscription(sessionId, subscriptionId);
            }

            // 방 브로드캐스트를 보낼지 판정할 근거다. 이게 없으면 발신자가 멤버 목록을 실어 보내야 한다.
            String roomId = StompDestination.CHAT_ROOM_PREFIX.uriOf(destination);
            if (roomId != null) {
                localSessionCache.registerRoomSubscription(sessionId, subscriptionId, roomId);
            }
        }

        log.debug("[ws] ttl refreshed by subscribe. instance-index={}, userId={}, sessionId={}", instanceId, userId, sessionId);
    }

    @EventListener
    public void handleUnsubscribe(SessionUnsubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());

        String sessionId = accessor.getSessionId();
        String subscriptionId = accessor.getSubscriptionId();

        if (sessionId == null || subscriptionId == null) return;

        localSessionCache.removeSubscription(sessionId, subscriptionId);
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());

        String sessionId = accessor.getSessionId();
        if (sessionId == null) {
            log.warn("[ws] disconnect ignored. instance-index={}, reason=sessionId-null", instanceId);
            return;
        }

        String userId = localSessionCache.findUserId(sessionId);

        if (userId != null) {
            sessionLocationPort.deleteIfServerMatches(userId, sessionId, instanceId);
            localSessionCache.remove(sessionId);
            activeSessions.updateAndGet(v -> Math.max(0, v - 1));
        }

        log.debug("[ws] disconnected instance-index={}, userId={}, sessionId={}, activeSessions={}", instanceId, userId != null ? userId : "unknown", sessionId, activeSessions.get());
    }
}
