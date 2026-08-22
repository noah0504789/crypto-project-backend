package org.example.websocket.gateway.adapter.in.websocket.stomp.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RedisChatMessageRateLimiterUnitTest {

    private static final RedisScript<Long> CHAT_MESSAGE_RATE_LIMIT_LUA =
            RedisScript.of(new ClassPathResource("META-INF/scripts/chatMessageRateLimit.lua"), Long.class);

    @Mock
    private StringRedisTemplate redisTemplate;

    @Test
    @DisplayName("사용자와 채팅방 Bucket을 모두 통과해야 메시지를 허용한다")
    @SuppressWarnings("unchecked")
    void isAllowed_shouldRequireBothBuckets() {
        RedisChatMessageRateLimiter rateLimiter = enabledRateLimiter();
        given(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any()))
                .willReturn(1L, 1L);

        boolean allowed = rateLimiter.isAllowed("user-1", "room-1");

        assertThat(allowed).isTrue();
        verify(redisTemplate, times(2))
                .execute(any(RedisScript.class), anyList(), any(), any());
    }

    @Test
    @DisplayName("사용자 Bucket이 거부하면 채팅방 Bucket은 소비하지 않는다")
    @SuppressWarnings("unchecked")
    void isAllowed_shouldStopWhenUserBucketRejects() {
        RedisChatMessageRateLimiter rateLimiter = enabledRateLimiter();
        given(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any()))
                .willReturn(0L);

        boolean allowed = rateLimiter.isAllowed("user-1", "room-1");

        assertThat(allowed).isFalse();
        verify(redisTemplate, times(1))
                .execute(any(RedisScript.class), anyList(), any(), any());
    }

    @Test
    @DisplayName("Redis 장애 시 ERROR 로그를 남기는 fail-open 경로로 메시지를 허용한다")
    @SuppressWarnings("unchecked")
    void isAllowed_shouldFailOpenWhenRedisFails() {
        RedisChatMessageRateLimiter rateLimiter = enabledRateLimiter();
        given(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any()))
                .willThrow(new DataAccessResourceFailureException("redis unavailable"));

        boolean allowed = rateLimiter.isAllowed("user-1", "room-1");

        assertThat(allowed).isTrue();
    }

    @Test
    @DisplayName("정책이 비활성화되면 Redis를 호출하지 않는다")
    @SuppressWarnings("unchecked")
    void isAllowed_shouldBypassRedisWhenDisabled() {
        ChatMessageRateLimitProperties properties = new ChatMessageRateLimitProperties(false, null, null);
        RedisChatMessageRateLimiter rateLimiter = new RedisChatMessageRateLimiter(redisTemplate, properties, CHAT_MESSAGE_RATE_LIMIT_LUA);

        boolean allowed = rateLimiter.isAllowed("user-1", "room-1");

        assertThat(allowed).isTrue();
        verify(redisTemplate, never())
                .execute(any(RedisScript.class), anyList(), any(), any());
    }

    private RedisChatMessageRateLimiter enabledRateLimiter() {
        ChatMessageRateLimitProperties properties = new ChatMessageRateLimitProperties(
                true,
                new ChatMessageRateLimitProperties.Bucket(3, 5),
                new ChatMessageRateLimitProperties.Bucket(30, 10)
        );
        return new RedisChatMessageRateLimiter(redisTemplate, properties, CHAT_MESSAGE_RATE_LIMIT_LUA);
    }
}
