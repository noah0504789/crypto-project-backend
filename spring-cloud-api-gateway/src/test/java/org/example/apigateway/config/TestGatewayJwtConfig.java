package org.example.apigateway.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@TestConfiguration
public class TestGatewayJwtConfig {

    @Bean
    @Primary
    public ReactiveJwtDecoder testReactiveJwtDecoder() {
        return token -> {
            if ("user-token".equals(token)) {
                return Mono.just(jwt(token, List.of("ROLE_USER")));
            }

            if ("no-role-token".equals(token)) {
                return Mono.just(jwt(token, List.of()));
            }

            if ("no-id-token".equals(token)) {
                return Mono.just(jwtWithoutId(token, List.of("ROLE_USER")));
            }

            return Mono.error(new RuntimeException("invalid token"));
        };
    }

    private Jwt jwt(String token, List<String> roles) {
        Instant now = Instant.now();

        return new Jwt(
                token,
                now,
                now.plusSeconds(3600),
                Map.of(
                        "alg", "none",
                        "typ", "JWT"
                ),
                Map.of(
                        "sub", "test-user@test.com",
                        "id", "user-1",
                        "roles", roles,
                        "iss", "test-issuer"
                )
        );
    }

    private Jwt jwtWithoutId(String token, List<String> roles) {
        Instant now = Instant.now();

        return new Jwt(
                token,
                now,
                now.plusSeconds(3600),
                Map.of(
                        "alg", "none",
                        "typ", "JWT"
                ),
                Map.of(
                        "sub", "test-user@test.com",
                        "roles", roles,
                        "iss", "test-issuer"
                )
        );
    }
}