package org.example.apigateway.filter;

import lombok.RequiredArgsConstructor;
import org.example.common.enums.HttpHeaderKey;
import org.example.common.enums.JwtClaimKey;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class IdentityPropagationGlobalFilter implements GlobalFilter, Ordered {

    private static final String FROM_HEADER = "X-From";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 클라이언트가 직접 보낸 신뢰 헤더는 입구에서 무조건 제거한다.
        // permitAll/미인증 경로로 들어와도 클라이언트가 위조한 X-User-Id가 하위 서비스로 새지 않게 한다.
        // 값은 오직 게이트웨이가 검증된 JWT로만 세팅한다.
        ServerWebExchange stripped = exchange.mutate()
                .request(request -> request.headers(headers -> {
                    headers.remove(HttpHeaderKey.USER_ID.value());
                    headers.remove(FROM_HEADER);
                }))
                .build();

        return stripped.getPrincipal()
                .cast(JwtAuthenticationToken.class)
                .filter(Authentication::isAuthenticated)
                .flatMap(auth -> Mono.justOrEmpty(auth.getTokenAttributes().get(JwtClaimKey.USER_ID.value())))
                .map(String::valueOf)
                .map(userId -> stripped.mutate()
                        .request(request -> request.headers(headers ->
                                headers.set(HttpHeaderKey.USER_ID.value(), userId)
                        ))
                        .build()
                )
                .defaultIfEmpty(stripped)
                .flatMap(chain::filter);
    }

    // 헤더 제거가 다른 필터(라우트 addRequestHeader 등)보다 먼저 일어나도록 최우선 순위로 실행한다.
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
