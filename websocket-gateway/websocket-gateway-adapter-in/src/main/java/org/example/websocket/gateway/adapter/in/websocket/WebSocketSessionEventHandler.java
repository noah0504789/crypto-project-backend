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

    public WebSocketSessionEventHandler(
            MeterRegistry registry,
            LocalSessionCache localSessionCache,
            SessionLocationPort sessionLocationPort,
            ApiPathProperties apiPathProperties
    ) {
        Gauge.builder("ws_active_sessions", activeSessions, AtomicInteger::get)
                .description("Current active WebSocket sessions")
                .register(registry);

        this.localSessionCache = localSessionCache;
        this.sessionLocationPort = sessionLocationPort;
        this.ackDestination = apiPathProperties.stomp().userDestinationPrefix()
                + StompDestination.CHAT_ACK_QUEUE.destination();
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

        log.info("[ws] connected instance-index={}, userId={}, sessionId={}, newSession={}, activeSessions={}", instanceId, userId, sessionId, isNewSession, activeSessions.get());
    }

    @EventListener
    public void handleSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());

        String sessionId = accessor.getSessionId();
        if (sessionId == null) return;

        String userId = localSessionCache.findUserId(sessionId);
        if (userId == null) return;

        sessionLocationPort.refreshTtl(userId);

        // ACK 를 brokerChannel 없이 직접 보내려면 구독 ID 가 필요하다. 여기서만 얻을 수 있다.
        if (ackDestination.equals(accessor.getDestination()) && accessor.getSubscriptionId() != null) {
            localSessionCache.registerAckSubscription(sessionId, accessor.getSubscriptionId());
        }

        log.debug("[ws] ttl refreshed by subscribe. instance-index={}, userId={}, sessionId={}", instanceId, userId, sessionId);
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

        log.info("[ws] disconnected instance-index={}, userId={}, sessionId={}, activeSessions={}", instanceId, userId != null ? userId : "unknown", sessionId, activeSessions.get());
    }
}
