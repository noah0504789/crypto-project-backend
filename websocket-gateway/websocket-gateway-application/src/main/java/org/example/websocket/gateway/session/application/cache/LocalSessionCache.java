package org.example.websocket.gateway.session.application.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LocalSessionCache {

    private final Cache<String, String> sessionToUser = Caffeine.newBuilder()
            .maximumSize(500_000)
            .build();

    private final Cache<String, Set<String>> userToSessions = Caffeine.newBuilder()
            .maximumSize(500_000)
            .build();

    // ACK 를 brokerChannel 없이 보내려면 구독 ID 가 필요하다. 없으면 클라이언트가 프레임을 매칭하지 못한다.
    private final Cache<String, String> sessionToAckSubscription = Caffeine.newBuilder()
            .maximumSize(500_000)
            .build();

    public void register(String sessionId, String userId) {
        sessionToUser.put(sessionId, userId);

        Set<String> sessions = userToSessions.get(userId, key -> ConcurrentHashMap.newKeySet());

        sessions.add(sessionId);
    }

    public String findUserId(String sessionId) {
        return sessionToUser.getIfPresent(sessionId);
    }

    public void registerAckSubscription(String sessionId, String subscriptionId) {
        sessionToAckSubscription.put(sessionId, subscriptionId);
    }

    public String findAckSubscriptionId(String sessionId) {
        return sessionToAckSubscription.getIfPresent(sessionId);
    }

    public Set<String> findSessions(String userId) {
        Set<String> sessions = userToSessions.getIfPresent(userId);
        return (sessions == null) ? Set.of() : Set.copyOf(sessions);
    }

    public boolean hasUser(String userId) {
        Set<String> sessions = userToSessions.getIfPresent(userId);
        return sessions != null && !sessions.isEmpty();
    }

    public void remove(String sessionId) {
        sessionToAckSubscription.invalidate(sessionId);

        String userId = sessionToUser.getIfPresent(sessionId);
        if (userId == null) return;

        sessionToUser.invalidate(sessionId);

        Set<String> sessions = userToSessions.getIfPresent(userId);
        if (sessions == null) return;

        sessions.remove(sessionId);

        if (sessions.isEmpty()) {
            userToSessions.invalidate(userId);
        }
    }
}
