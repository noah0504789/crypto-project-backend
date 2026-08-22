package org.example.websocket.gateway.adapter.in.websocket.stomp.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class RedisChatMessageRateLimiter {

    private static final String USER_KEY_PREFIX = "rate-limit:stomp:chat-message:user:";
    private static final String ROOM_KEY_PREFIX = "rate-limit:stomp:chat-message:room:";

    private final StringRedisTemplate redisTemplate;
    private final ChatMessageRateLimitProperties properties;
    private final RedisScript<Long> chatMessageRateLimit_lua;

    public RedisChatMessageRateLimiter(
            StringRedisTemplate redisTemplate,
            ChatMessageRateLimitProperties properties,
            RedisScript<Long> chatMessageRateLimit_lua
    ) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.chatMessageRateLimit_lua = chatMessageRateLimit_lua;
    }

    public boolean isAllowed(String userId, String roomId) {
        if (!properties.enabled()) {
            return true;
        }

        try {
            if (!consume(USER_KEY_PREFIX + userId, properties.user())) {
                return false;
            }

            return consume(ROOM_KEY_PREFIX + roomId, properties.room());
        } catch (RuntimeException e) {
            log.error("[stomp-rate-limit] Redis error; chat message allowed by fail-open policy", e);
            return true;
        }
    }

    private boolean consume(String key, ChatMessageRateLimitProperties.Bucket bucket) {
        Long result = redisTemplate.execute(
                chatMessageRateLimit_lua,
                List.of(key),
                Long.toString(bucket.replenishRate()),
                Long.toString(bucket.burstCapacity())
        );
        return Long.valueOf(1L).equals(result);
    }
}
