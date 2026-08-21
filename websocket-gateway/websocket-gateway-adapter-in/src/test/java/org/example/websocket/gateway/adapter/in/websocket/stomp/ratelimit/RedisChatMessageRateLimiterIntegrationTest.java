package org.example.websocket.gateway.adapter.in.websocket.stomp.ratelimit;

import org.example.common.test.testcontainer.RedisTestContainerInitializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
        "app.rate-limit.chat-message.enabled=true",
        "app.rate-limit.chat-message.user.replenish-rate=1",
        "app.rate-limit.chat-message.user.burst-capacity=3",
        "app.rate-limit.chat-message.room.replenish-rate=1",
        "app.rate-limit.chat-message.room.burst-capacity=10"
})
@ContextConfiguration(
        classes = RedisChatMessageRateLimiterIntegrationTest.TestApplication.class,
        initializers = RedisTestContainerInitializer.class
)
class RedisChatMessageRateLimiterIntegrationTest {

    @Autowired
    private RedisChatMessageRateLimiter rateLimiter;

    @Test
    @DisplayName("실제 Redis Lua Bucket은 사용자 Burst 3개 허용 후 네 번째 메시지를 거부한다")
    void isAllowed_shouldEnforceUserBurstCapacity() {
        String suffix = UUID.randomUUID().toString();

        assertThat(rateLimiter.isAllowed("user-" + suffix, "room-" + suffix)).isTrue();
        assertThat(rateLimiter.isAllowed("user-" + suffix, "room-" + suffix)).isTrue();
        assertThat(rateLimiter.isAllowed("user-" + suffix, "room-" + suffix)).isTrue();
        assertThat(rateLimiter.isAllowed("user-" + suffix, "room-" + suffix)).isFalse();
    }

    @Test
    @DisplayName("실제 Redis Lua Bucket은 서로 다른 사용자의 같은 방 Burst를 10개로 제한한다")
    void isAllowed_shouldEnforceRoomBurstCapacity() {
        String roomId = "room-" + UUID.randomUUID();

        for (int index = 0; index < 10; index++) {
            assertThat(rateLimiter.isAllowed("user-" + UUID.randomUUID(), roomId)).isTrue();
        }
        assertThat(rateLimiter.isAllowed("user-" + UUID.randomUUID(), roomId)).isFalse();
    }

    @EnableAutoConfiguration
    @EnableConfigurationProperties(ChatMessageRateLimitProperties.class)
    @Import(RedisChatMessageRateLimiter.class)
    static class TestApplication {
    }
}
