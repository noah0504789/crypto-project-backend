package org.example.apigateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.example.common.enums.JwtClaimKey;
import org.example.common.enums.RoleKey;
import org.example.common.properties.ApiPathProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.security.web.server.header.XFrameOptionsServerHttpHeadersWriter;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
public class ReactiveSecurityConfig {

    private final ObjectMapper objectMapper;
    private final ApiPathProperties apiPathProperties;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, ReactiveJwtDecoder reactiveJwtDecoder) {
        ServerAuthenticationEntryPoint authenticationEntryPoint = (exchange, exception) -> writeError(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication is required");
        ServerAccessDeniedHandler accessDeniedHandler = (exchange, exception) -> writeError(exchange, HttpStatus.FORBIDDEN, "FORBIDDEN", "Access is denied");

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(Customizer.withDefaults())
                .headers(headers -> headers.frameOptions(frameOptions ->
                        frameOptions.mode(XFrameOptionsServerHttpHeadersWriter.Mode.SAMEORIGIN)
                ))
                .authorizeExchange(authz -> authz
                        .pathMatchers(HttpMethod.OPTIONS, apiPathProperties.optionsPattern()).permitAll()

                        // Deployment control endpoint
                        // JWT 인증은 우회시키고, 실제 배포 토큰 검증은 DeploymentController/Filter에서 처리
                        .pathMatchers(HttpMethod.POST, "/internal/deployment/**").permitAll()

                        .pathMatchers(apiPathProperties.oauth2().pattern(), apiPathProperties.oauth2().loginCallbackPattern()).permitAll()
                        .pathMatchers(HttpMethod.GET, apiPathProperties.websocket().infoPattern()).permitAll()
                        .pathMatchers(apiPathProperties.websocket().msgPattern()).permitAll()

                        .pathMatchers(HttpMethod.GET, apiPathProperties.user().mePath(), apiPathProperties.user().profilePattern())
                        .hasRole(RoleKey.REQUIRED_USER.value())

                        .pathMatchers(HttpMethod.PATCH, apiPathProperties.user().mePath())
                        .hasRole(RoleKey.REQUIRED_USER.value())

                        .pathMatchers(HttpMethod.GET, apiPathProperties.websocket().pattern(), apiPathProperties.websocket().nativePath(), apiPathProperties.websocket().nativePattern())
                        .hasRole(RoleKey.REQUIRED_USER.value())

                        .pathMatchers(HttpMethod.GET, apiPathProperties.chat().roomsMe(), apiPathProperties.chat().roomMePattern())
                        .hasRole(RoleKey.REQUIRED_USER.value())

                        .pathMatchers(apiPathProperties.chat().roomMembersPattern(), apiPathProperties.chat().roomActivityPattern())
                        .hasRole(RoleKey.REQUIRED_USER.value())

                        .pathMatchers(HttpMethod.GET, apiPathProperties.chat().roomMessagesPattern())
                        .hasRole(RoleKey.REQUIRED_USER.value())

                        .pathMatchers(HttpMethod.POST, apiPathProperties.chat().roomCreate())
                        .hasRole(RoleKey.REQUIRED_USER.value())

                        .pathMatchers(HttpMethod.PATCH, apiPathProperties.chat().roomPattern())
                        .hasRole(RoleKey.REQUIRED_USER.value())

                        .pathMatchers(HttpMethod.DELETE, apiPathProperties.chat().roomPattern())
                        .hasRole(RoleKey.REQUIRED_USER.value())

                        .pathMatchers(HttpMethod.GET, apiPathProperties.chat().roomPattern())
                        .hasRole(RoleKey.REQUIRED_USER.value())

                        // market: 카탈로그 조회는 공개, 내 가격알림 설정은 인증 필요(X-User-Id 스코프)
                        .pathMatchers(HttpMethod.GET, apiPathProperties.market().markets()).permitAll()
                        .pathMatchers(apiPathProperties.market().priceAlertsPattern())
                        .hasRole(RoleKey.REQUIRED_USER.value())

                        // notification: 내 알림함/읽음은 인증 필요(X-User-Id 스코프)
                        .pathMatchers(apiPathProperties.notification().notificationsPattern())
                        .hasRole(RoleKey.REQUIRED_USER.value())

                        .pathMatchers(apiPathProperties.actuatorPattern()).permitAll()
                        .pathMatchers(apiPathProperties.auth().pattern()).permitAll()
                        .pathMatchers(apiPathProperties.user().pattern()).permitAll()
                        .pathMatchers(apiPathProperties.chat().pattern()).permitAll()

                        .anyExchange().denyAll()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtDecoder(reactiveJwtDecoder)
                                .jwtAuthenticationConverter(jwtAuthenticationConverter())
                        )
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .build();
    }

    private Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter gac = new JwtGrantedAuthoritiesConverter();
        gac.setAuthoritiesClaimName(JwtClaimKey.ROLES.value());
        gac.setAuthorityPrefix("");

        ReactiveJwtAuthenticationConverter conv = new ReactiveJwtAuthenticationConverter();
        conv.setJwtGrantedAuthoritiesConverter(jwt -> Flux.fromIterable(gac.convert(jwt)));

        return conv;
    }

    private Mono<Void> writeError(
            ServerWebExchange exchange,
            HttpStatus status,
            String error,
            String message
    ) {
        ServerHttpResponse response = exchange.getResponse();

        if (response.isCommitted()) {
            return Mono.empty();
        }

        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        if (status == HttpStatus.UNAUTHORIZED) {
            response.getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }

        Map<String, Object> body = Map.of(
                "timestamp", Instant.now().toString(),
                "status", status.value(),
                "error", error,
                "message", message,
                "path", exchange.getRequest().getPath().value()
        );

        byte[] bytes;

        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (Exception e) {
            bytes = ("{\"status\":" + status.value() + "}").getBytes(StandardCharsets.UTF_8);
        }

        return response.writeWith(
                Mono.just(response.bufferFactory().wrap(bytes))
        );
    }
}
