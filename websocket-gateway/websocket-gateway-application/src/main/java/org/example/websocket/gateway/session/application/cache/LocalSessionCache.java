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

    private final Cache<String, SessionSubscriptions> sessionToSubscriptions = Caffeine.newBuilder()
            .maximumSize(500_000)
            .build();

    // 방 브로드캐스트를 보낼지 말지는 "이 방을 구독한 세션이 여기 있나" 하나로 정해진다.
    // evict 되면 구독자가 있는데 없다고 답해 브로드캐스트를 통째로 건너뛰므로 캐시가 아니라 맵이다.
    // 마지막 구독자가 빠질 때 키까지 지우므로 무한히 자라지 않는다.
    private final ConcurrentHashMap<String, Set<String>> roomToSessions = new ConcurrentHashMap<>();

    public void register(String sessionId, String userId) {
        sessionToUser.put(sessionId, userId);

        Set<String> sessions = userToSessions.get(userId, key -> ConcurrentHashMap.newKeySet());

        sessions.add(sessionId);
    }

    public String findUserId(String sessionId) {
        return sessionToUser.getIfPresent(sessionId);
    }

    public void registerAckSubscription(String sessionId, String subscriptionId) {
        subscriptionsOf(sessionId).ackSubscriptionId = subscriptionId;
    }

    public String findAckSubscriptionId(String sessionId) {
        SessionSubscriptions subscriptions = sessionToSubscriptions.getIfPresent(sessionId);
        return (subscriptions == null) ? null : subscriptions.ackSubscriptionId;
    }

    public void registerRoomSubscription(String sessionId, String subscriptionId, String roomId) {
        roomToSessions.computeIfAbsent(roomId, key -> ConcurrentHashMap.newKeySet()).add(sessionId);
        subscriptionsOf(sessionId).rooms.put(subscriptionId, roomId);
    }

    public void removeRoomSubscription(String sessionId, String subscriptionId) {
        SessionSubscriptions subscriptions = sessionToSubscriptions.getIfPresent(sessionId);
        if (subscriptions == null) return;

        String roomId = subscriptions.rooms.remove(subscriptionId);
        if (roomId == null) return;

        // 한 세션이 같은 방을 여러 구독으로 열 수 있다. 남은 구독이 없을 때만 방에서 뗀다.
        if (!subscriptions.rooms.containsValue(roomId)) {
            detachRoom(sessionId, roomId);
        }
    }

    public boolean hasLocalSubscriber(String roomId) {
        Set<String> sessions = roomToSessions.get(roomId);
        return sessions != null && !sessions.isEmpty();
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
        SessionSubscriptions subscriptions = sessionToSubscriptions.getIfPresent(sessionId);

        if (subscriptions != null) {
            sessionToSubscriptions.invalidate(sessionId);
            subscriptions.rooms.values().forEach(roomId -> detachRoom(sessionId, roomId));
        }

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

    private SessionSubscriptions subscriptionsOf(String sessionId) {
        return sessionToSubscriptions.get(sessionId, key -> new SessionSubscriptions());
    }

    // 마지막 구독자가 빠지면 방 항목까지 지운다. 남겨두면 아무도 듣지 않는 방으로 계속 발송한다.
    private void detachRoom(String sessionId, String roomId) {
        roomToSessions.computeIfPresent(roomId, (key, sessions) -> {
            sessions.remove(sessionId);
            return sessions.isEmpty() ? null : sessions;
        });
    }

    /** 세션 하나가 들고 있는 구독. ACK 와 방 구독은 키도 수명도 같아 함께 둔다. */
    private static final class SessionSubscriptions {

        // ACK 를 brokerChannel 없이 보내려면 구독 ID 가 필요하다. 없으면 클라이언트가 프레임을 매칭하지 못한다.
        private volatile String ackSubscriptionId;

        // UNSUBSCRIBE 프레임에는 목적지가 없고 구독 ID 만 온다. 그 ID 로 방을 되찾는다.
        private final ConcurrentHashMap<String, String> rooms = new ConcurrentHashMap<>();
    }
}
