package org.example.apigateway.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitConfigUnitTest {

    @Test
    @DisplayName("Route ID별 Redis Bucket 정책을 등록한다")
    void gatewayRedisRateLimiter_shouldRegisterRoutePolicies() {
        GatewayRateLimitProperties.Bucket perMinute = new GatewayRateLimitProperties.Bucket(5, 120, 60);
        GatewayRateLimitProperties.Bucket perSecond = new GatewayRateLimitProperties.Bucket(2, 5, 1);
        GatewayRateLimitProperties properties = new GatewayRateLimitProperties(
                perMinute,
                perMinute,
                perMinute,
                perMinute,
                perMinute,
                perSecond,
                perSecond,
                perSecond,
                perSecond
        );

        RedisRateLimiter rateLimiter = new RateLimitConfig().gatewayRedisRateLimiter(properties);

        assertThat(rateLimiter.getConfig()).hasSize(17);
        assertThat(rateLimiter.getConfig().get(RateLimitedRouteId.USER_SIGN_UP).getReplenishRate())
                .isEqualTo(5);
        assertThat(rateLimiter.getConfig().get(RateLimitedRouteId.USER_SIGN_UP).getRequestedTokens())
                .isEqualTo(60);
        assertThat(rateLimiter.getConfig().get(RateLimitedRouteId.WEBSOCKET_HANDSHAKE).getBurstCapacity())
                .isEqualTo(5);
        assertThat(rateLimiter.getConfig().get(RateLimitedRouteId.CHAT_COMMAND).getReplenishRate())
                .isEqualTo(2);
    }
}
