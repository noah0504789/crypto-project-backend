package org.example.websocket.gateway.session.application.cache;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 이 인스턴스에 붙은 세션과 그 구독. 넷 다 캐시가 아니라 맵이다 — 상한 축출이 정리 경로를
 * 끊는 이유는 {@code docs/modules/WEBSOCKET_GATEWAY.md} §7.
 */
@Component
public class LocalSessionCache {

    private final Map<String, String> sessionToUser = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> userToSessions = new ConcurrentHashMap<>();
    private final Map<String, SessionSubscriptions> sessionToSubscriptions = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> roomToSessions = new ConcurrentHashMap<>();

    public void register(String sessionId, String userId) {
        String previousUserId = sessionToUser.put(sessionId, userId);

        if (previousUserId != null && !previousUserId.equals(userId)) {
            detachSession(previousUserId, sessionId);
        }

        userToSessions.computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet()).add(sessionId);
    }

    public String findUserId(String sessionId) {
        return sessionToUser.get(sessionId);
    }

    public void registerAckSubscription(String sessionId, String subscriptionId) {
        subscriptionsOf(sessionId).ackSubscriptionId = subscriptionId;
    }

    public String findAckSubscriptionId(String sessionId) {
        SessionSubscriptions subscriptions = sessionToSubscriptions.get(sessionId);
        return (subscriptions == null) ? null : subscriptions.ackSubscriptionId;
    }

    public void registerRoomSubscription(String sessionId, String subscriptionId, String roomId) {
        roomToSessions.computeIfAbsent(roomId, key -> ConcurrentHashMap.newKeySet()).add(sessionId);
        subscriptionsOf(sessionId).rooms.put(subscriptionId, roomId);
    }

    public void removeRoomSubscription(String sessionId, String subscriptionId) {
        SessionSubscriptions subscriptions = sessionToSubscriptions.get(sessionId);
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
        Set<String> sessions = userToSessions.get(userId);
        return (sessions == null) ? Set.of() : Set.copyOf(sessions);
    }

    public boolean hasUser(String userId) {
        Set<String> sessions = userToSessions.get(userId);
        return sessions != null && !sessions.isEmpty();
    }

    public int sessionCount() {
        return sessionToUser.size();
    }

    public int subscribedRoomCount() {
        return roomToSessions.size();
    }

    // 구독을 먼저 걷어낸다. 세션이 어느 방에 있었는지는 이 항목에만 있어서 순서가 바뀌면 방에 죽은 세션이 남는다.
    public void remove(String sessionId) {
        SessionSubscriptions subscriptions = sessionToSubscriptions.remove(sessionId);

        if (subscriptions != null) {
            subscriptions.rooms.values().forEach(roomId -> detachRoom(sessionId, roomId));
        }

        String userId = sessionToUser.remove(sessionId);
        if (userId == null) return;

        detachSession(userId, sessionId);
    }

    private SessionSubscriptions subscriptionsOf(String sessionId) {
        return sessionToSubscriptions.computeIfAbsent(sessionId, key -> new SessionSubscriptions());
    }

    // 마지막 구독자가 빠지면 방 항목까지 지운다. 남겨두면 아무도 듣지 않는 방으로 계속 발송한다.
    private void detachRoom(String sessionId, String roomId) {
        roomToSessions.computeIfPresent(roomId, (key, sessions) -> {
            sessions.remove(sessionId);
            return sessions.isEmpty() ? null : sessions;
        });
    }

    // 비었는지 확인하고 지우는 것을 한 번에 한다. 나눠 하면 그 사이 들어온 세션까지 함께 지워진다.
    private void detachSession(String userId, String sessionId) {
        userToSessions.computeIfPresent(userId, (key, sessions) -> {
            sessions.remove(sessionId);
            return sessions.isEmpty() ? null : sessions;
        });
    }

    /** 세션 하나가 들고 있는 구독. ACK 와 방 구독은 키도 수명도 같아 함께 둔다. */
    private static final class SessionSubscriptions {

        // ACK 를 brokerChannel 없이 보내려면 구독 ID 가 필요하다. 없으면 클라이언트가 프레임을 매칭하지 못한다.
        private volatile String ackSubscriptionId;

        // UNSUBSCRIBE 프레임에는 목적지가 없고 구독 ID 만 온다. 그 ID 로 방을 되찾는다.
        private final Map<String, String> rooms = new ConcurrentHashMap<>();
    }
}
