package org.example.websocket.gateway.session.adapter.out.redis;

import lombok.RequiredArgsConstructor;
import org.example.common.redis.operation.StringRedisHashOperations;
import org.example.websocket.gateway.session.application.out.SessionLocationPort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

import static org.example.common.enums.RedisKey.SESSION_INFO;

@Component
@RequiredArgsConstructor
public class RedisSessionLocationAdapter implements SessionLocationPort {

    private static final Duration SESSION_TTL = Duration.ofMinutes(3); // TODO: 주입받기

    private final StringRedisHashOperations hash;
    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public void save(String userId, String sessionId, String serverId) {
        String sessionInfoKey = SESSION_INFO.keyFor(userId);

        hash.update(sessionInfoKey, sessionId, serverId);
        refreshTtl(userId);
    }

    @Override
    public void deleteIfServerMatches(String userId, String sessionId, String expectedServerId) {
        String sessionInfoKey = SESSION_INFO.keyFor(userId);
        String curServerId = hash.findField(sessionInfoKey, sessionId);
        if (!expectedServerId.equals(curServerId)) return;

        delete(userId, sessionId);
    }

    @Override
    public void delete(String userId, String sessionId) {
        String sessionInfoKey = SESSION_INFO.keyFor(userId);

        hash.deleteField(sessionInfoKey, sessionId);

        Long size = hash.size(sessionInfoKey);
        if (size != null && size == 0) redisTemplate.delete(sessionInfoKey);
    }

    @Override
    public void refreshTtl(String userId) {
        String sessionInfoKey = SESSION_INFO.keyFor(userId);

        redisTemplate.expire(sessionInfoKey, SESSION_TTL);
    }
}
