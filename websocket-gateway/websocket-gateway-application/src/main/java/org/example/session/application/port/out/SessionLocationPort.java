package org.example.session.application.port.out;

public interface SessionLocationPort {
    void save(String userId, String sessionId, String serverId);

    void deleteIfServerMatches(String userId, String sessionId, String expectedServerId);

    void delete(String userId, String sessionId);

    void refreshTtl(String userId);
}
