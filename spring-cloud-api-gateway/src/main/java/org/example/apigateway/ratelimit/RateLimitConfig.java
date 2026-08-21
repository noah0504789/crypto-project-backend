package org.example.apigateway.ratelimit;

import java.net.InetSocketAddress;
import org.example.common.enums.JwtClaimKey;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimitConfig {

    @Bean
    public GatewayKeyResolvers gatewayKeyResolvers() {
        KeyResolver ip = exchange -> Mono.justOrEmpty(exchange.getRequest().getRemoteAddress())
                .map(InetSocketAddress::getAddress)
                .map(address -> "ip:" + address.getHostAddress());

        KeyResolver user = exchange -> exchange.getPrincipal()
                .ofType(JwtAuthenticationToken.class)
                .filter(Authentication::isAuthenticated)
                .map(authentication -> authentication.getToken().getClaimAsString(JwtClaimKey.USER_ID.value()))
                .filter(StringUtils::hasText)
                .map(userId -> "user:" + userId);

        KeyResolver userOrIp = exchange -> user.resolve(exchange).switchIfEmpty(ip.resolve(exchange));
        return new GatewayKeyResolvers(ip, user, userOrIp);
    }

    @Bean
    public RedisRateLimiter gatewayRedisRateLimiter(GatewayRateLimitProperties properties) {
        GatewayRateLimitProperties.Bucket defaultBucket = properties.publicQuery();
        RedisRateLimiter rateLimiter = new RedisRateLimiter(
                defaultBucket.replenishRate(),
                defaultBucket.burstCapacity(),
                defaultBucket.requestedTokens()
        );

        register(rateLimiter, RateLimitedRouteId.OAUTH2_AUTHORIZATION, properties.oauth2Authorization());
        register(rateLimiter, RateLimitedRouteId.OAUTH2_CALLBACK, properties.oauth2Callback());
        register(rateLimiter, RateLimitedRouteId.TOKEN_REFRESH, properties.tokenRefresh());
        register(rateLimiter, RateLimitedRouteId.LOGOUT, properties.logout());
        register(rateLimiter, RateLimitedRouteId.USER_SIGN_UP, properties.signUp());
        register(rateLimiter, RateLimitedRouteId.USER_COMMAND, properties.command());
        register(rateLimiter, RateLimitedRouteId.USER_QUERY, properties.query());
        register(rateLimiter, RateLimitedRouteId.WEBSOCKET_NATIVE_HANDSHAKE, properties.websocketHandshake());
        register(rateLimiter, RateLimitedRouteId.WEBSOCKET_HANDSHAKE, properties.websocketHandshake());
        register(rateLimiter, RateLimitedRouteId.CHAT_COMMAND, properties.command());
        register(rateLimiter, RateLimitedRouteId.CHAT_QUERY, properties.query());
        register(rateLimiter, RateLimitedRouteId.CHAT_PUBLIC_QUERY, properties.publicQuery());
        register(rateLimiter, RateLimitedRouteId.MARKET_COMMAND, properties.command());
        register(rateLimiter, RateLimitedRouteId.MARKET_QUERY, properties.query());
        register(rateLimiter, RateLimitedRouteId.MARKET_PUBLIC_QUERY, properties.publicQuery());
        register(rateLimiter, RateLimitedRouteId.NOTIFICATION_COMMAND, properties.command());
        register(rateLimiter, RateLimitedRouteId.NOTIFICATION_QUERY, properties.query());
        return rateLimiter;
    }

    private void register(
            RedisRateLimiter rateLimiter,
            String routeId,
            GatewayRateLimitProperties.Bucket bucket
    ) {
        RedisRateLimiter.Config config = new RedisRateLimiter.Config()
                .setReplenishRate(bucket.replenishRate())
                .setBurstCapacity(bucket.burstCapacity())
                .setRequestedTokens(bucket.requestedTokens());
        rateLimiter.getConfig().put(routeId, config);
    }
}
