package org.example.apigateway.ratelimit;

import java.util.UUID;
import org.example.common.test.testcontainer.RedisTestContainerInitializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.test.context.ContextConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ContextConfiguration(initializers = RedisTestContainerInitializer.class)
class RedisRateLimiterIntegrationTest {

    @Autowired
    private RedisRateLimiter rateLimiter;

    @Test
    @DisplayName("회원가입 Bucket은 순간 두 요청을 허용하고 세 번째 요청을 거부한다")
    void signUpBucket_shouldEnforceBurstCapacity() {
        String key = "ip:integration-" + UUID.randomUUID();

        RateLimiter.Response first = rateLimiter.isAllowed(RateLimitedRouteId.USER_SIGN_UP, key).block();
        RateLimiter.Response second = rateLimiter.isAllowed(RateLimitedRouteId.USER_SIGN_UP, key).block();
        RateLimiter.Response third = rateLimiter.isAllowed(RateLimitedRouteId.USER_SIGN_UP, key).block();

        assertThat(first).isNotNull();
        assertThat(first.isAllowed()).isTrue();
        assertThat(second).isNotNull();
        assertThat(second.isAllowed()).isTrue();
        assertThat(third).isNotNull();
        assertThat(third.isAllowed()).isFalse();
    }
}
