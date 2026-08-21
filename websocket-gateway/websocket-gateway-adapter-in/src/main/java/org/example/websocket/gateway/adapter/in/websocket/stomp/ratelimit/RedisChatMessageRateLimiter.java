package org.example.websocket.gateway.adapter.in.websocket.stomp.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class RedisChatMessageRateLimiter {

    private static final String USER_KEY_PREFIX = "rate-limit:stomp:chat-message:user:";
    private static final String ROOM_KEY_PREFIX = "rate-limit:stomp:chat-message:room:";

    private static final DefaultRedisScript<Long> TOKEN_BUCKET_SCRIPT = new DefaultRedisScript<>("""
            local rate = tonumber(ARGV[1])
            local capacity = tonumber(ARGV[2])
            local redis_time = redis.call('TIME')
            local now_ms = (redis_time[1] * 1000) + math.floor(redis_time[2] / 1000)
            local state = redis.call('HMGET', KEYS[1], 'tokens', 'timestamp')
            local tokens = tonumber(state[1])
            local timestamp = tonumber(state[2])

            if tokens == nil then tokens = capacity end
            if timestamp == nil then timestamp = now_ms end

            local elapsed_seconds = math.max(0, now_ms - timestamp) / 1000
            local filled_tokens = math.min(capacity, tokens + (elapsed_seconds * rate))
            local allowed = filled_tokens >= 1

            if allowed then filled_tokens = filled_tokens - 1 end

            redis.call('HSET', KEYS[1], 'tokens', filled_tokens, 'timestamp', now_ms)
            local ttl_ms = math.max(60000, math.ceil((capacity / rate) * 2000))
            redis.call('PEXPIRE', KEYS[1], ttl_ms)

            if allowed then return 1 else return 0 end
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ChatMessageRateLimitProperties properties;

    public RedisChatMessageRateLimiter(
            StringRedisTemplate redisTemplate,
            ChatMessageRateLimitProperties properties
    ) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
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
                TOKEN_BUCKET_SCRIPT,
                List.of(key),
                Long.toString(bucket.replenishRate()),
                Long.toString(bucket.burstCapacity())
        );
        return Long.valueOf(1L).equals(result);
    }
}
