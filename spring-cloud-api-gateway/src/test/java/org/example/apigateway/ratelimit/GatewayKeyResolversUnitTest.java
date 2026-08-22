package org.example.apigateway.ratelimit;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayKeyResolversUnitTest {

    private final GatewayKeyResolvers keyResolvers = new RateLimitConfig().gatewayKeyResolvers();

    @Test
    @DisplayName("IP Key는 직접 연결된 Client의 Remote Address를 사용한다")
    void ip_shouldResolveRemoteAddress() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/user/sign-up")
                        .remoteAddress(new InetSocketAddress("203.0.113.10", 54321))
        );

        String key = keyResolvers.ip().resolve(exchange).block();

        assertThat(key).isEqualTo("ip:203.0.113.10");
    }

    @Test
    @DisplayName("User Key는 Principal name 대신 검증된 JWT id Claim을 사용한다")
    void user_shouldResolveJwtUserIdClaim() {
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(
                jwt(Map.of("sub", "user@example.com", "id", "user-123")),
                List.of(),
                "user@example.com"
        );
        ServerWebExchange exchange = MockServerWebExchange.from(
                        MockServerHttpRequest.get("/user/me/profile")
                ).mutate()
                .principal(Mono.just(authentication))
                .build();

        String key = keyResolvers.user().resolve(exchange).block();

        assertThat(key).isEqualTo("user:user-123");
    }

    @Test
    @DisplayName("User 또는 IP Key는 인증 정보가 없으면 Remote Address로 대체한다")
    void userOrIp_shouldFallbackToRemoteAddress() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/auth/logout")
                        .remoteAddress(new InetSocketAddress("198.51.100.7", 54321))
        );

        String key = keyResolvers.userOrIp().resolve(exchange).block();

        assertThat(key).isEqualTo("ip:198.51.100.7");
    }

    private Jwt jwt(Map<String, Object> claims) {
        Instant now = Instant.now();
        return new Jwt(
                "token",
                now,
                now.plusSeconds(3600),
                Map.of("alg", "none", "typ", "JWT"),
                claims
        );
    }
}
