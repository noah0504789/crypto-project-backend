package org.example.websocket.gateway.session.application.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

/**
 * ACK 목적지에 대한 세션별 구독 ID. brokerChannel 을 건너뛰고 클라이언트로 직접 보낼 때 필요하다.
 * 구독 ID 가 없으면 STOMP MESSAGE 프레임이 반쪽이 되어 클라이언트가 매칭하지 못한다.
 */
@Component
public class AckSubscriptionRegistry {

    private final Cache<String, String> sessionToSubscription = Caffeine.newBuilder()
            .maximumSize(500_000)
            .build();

    public void register(String sessionId, String subscriptionId) {
        sessionToSubscription.put(sessionId, subscriptionId);
    }

    public String findSubscriptionId(String sessionId) {
        return sessionToSubscription.getIfPresent(sessionId);
    }

    public void remove(String sessionId) {
        sessionToSubscription.invalidate(sessionId);
    }
}
