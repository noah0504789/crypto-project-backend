package org.example.gateway.filter;

import lombok.RequiredArgsConstructor;
import org.example.common.enums.HttpHeaderKey;
import org.example.common.enums.JwtClaimKey;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class IdentityPropagationGlobalFilter implements GlobalFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return exchange.getPrincipal()
                .cast(JwtAuthenticationToken.class)
                .filter(Authentication::isAuthenticated)
                .flatMap(auth -> Mono.justOrEmpty(auth.getTokenAttributes().get(JwtClaimKey.USER_ID.value())))
                .map(String::valueOf)
                .map(userId -> exchange.mutate()
                        .request(request -> request.headers(headers ->
                                headers.set(HttpHeaderKey.USER_ID.value(), userId)
                        ))
                        .build()
                )
                .defaultIfEmpty(exchange)
                .flatMap(chain::filter);
    }
}
