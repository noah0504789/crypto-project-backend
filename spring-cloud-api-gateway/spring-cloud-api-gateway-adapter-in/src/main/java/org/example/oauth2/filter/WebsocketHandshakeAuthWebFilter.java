package org.example.oauth2.filter;

import lombok.RequiredArgsConstructor;
import org.example.common.enums.AuthTokenKey;
import org.example.common.enums.HttpHeaderKey;
import org.example.common.enums.JwtClaimKey;
import org.example.common.properties.ApiPathProperties;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;

@Component
@Order(-1000)
@RequiredArgsConstructor
public class WebsocketHandshakeAuthWebFilter implements WebFilter {

    private final ReactiveJwtDecoder jwtDecoder;
    private final ApiPathProperties apiPathProperties;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        HttpMethod method = request.getMethod();

        if (!isWebSocketPath(path) || HttpMethod.OPTIONS.equals(method)) {
            return chain.filter(exchange);
        }

        String accessToken = request.getQueryParams().getFirst(AuthTokenKey.ACCESS_TOKEN_QUERY.value());
        if (accessToken == null || accessToken.isBlank()) {
            return unauthorized(exchange);
        }

        return jwtDecoder.decode(accessToken)
                .flatMap(jwt -> authenticate(exchange, chain, jwt))
                .onErrorResume(e -> unauthorized(exchange));
    }

    private boolean isWebSocketPath(String path) {
        return path.startsWith(apiPathProperties.websocket().prefix());
    }

    private Mono<Void> authenticate(ServerWebExchange exchange, WebFilterChain chain, Jwt jwt) {
        String userId = jwt.getClaimAsString(JwtClaimKey.USER_ID.value());

        if (userId == null || userId.isBlank()) {
            return unauthorized(exchange);
        }

        List<SimpleGrantedAuthority> authorities = resolveAuthorities(jwt);

        ServerHttpRequest mutatedRequest = exchange.getRequest()
                .mutate()
                .headers(headers -> headers.set(HttpHeaderKey.USER_ID.value(), userId))
                .build();

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(mutatedRequest)
                .build();

        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt, authorities, userId);

        return chain.filter(mutatedExchange)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
    }

    private List<SimpleGrantedAuthority> resolveAuthorities(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList(JwtClaimKey.ROLES.value());

        if (roles == null || roles.isEmpty()) {
            return List.of();
        }

        return roles.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(role -> !role.isBlank())
                .map(SimpleGrantedAuthority::new)
                .toList();
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }
}
