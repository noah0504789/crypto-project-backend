package org.example.apigateway.filter;

import org.example.common.properties.ApiPathProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WebsocketHandshakeAuthWebFilterTest {

    private static final String VALID_TOKEN = "valid-access-token";
    private static final String INVALID_TOKEN = "invalid-access-token";
    private static final String USER_ID = "user-1";

    @Mock
    private ReactiveJwtDecoder jwtDecoder;

    @Mock
    private WebFilterChain chain;

    private WebsocketHandshakeAuthWebFilter sut;

    @BeforeEach
    void setUp() {
        sut = new WebsocketHandshakeAuthWebFilter(jwtDecoder, apiPathProperties());
    }

    private ApiPathProperties apiPathProperties() {
        return new ApiPathProperties(
                new ApiPathProperties.Websocket("/ws", "/ws-native", "/ws", "/ws-native", "/ws-native/**", "/ws/**", "/msg/**", "/ws/info/**"),
                new ApiPathProperties.Stomp("/msg", "/user"),
                new ApiPathProperties.Auth("/auth/**", "/auth/logout"),
                new ApiPathProperties.OAuth2("/oauth2/authorization", "/oauth2/**", "/login/oauth2/code/**", "/login/oauth2/code", "/login?error", "/oauth2/**"),
                new ApiPathProperties.Chat(
                        "/chat/**",
                        "/chat/rooms/popular",
                        "/chat/rooms/me",
                        "/chat/room/*",
                        "/chat/room/me/*",
                        "/chat/room/*/members",
                        "/chat/room/*/activity",
                        "/chat/room",
                        "/chat/room/*/messages",
                        "/chat/**",
                        "/chat/room/me/*",
                        "/chat/room/*/members",
                        "/chat/room/*/activity",
                        "/chat/room/*/messages"
                ),
                new ApiPathProperties.User("/user", "/sign-up", "/me/profile", "/{publicId}/profile"),
                new ApiPathProperties.Route("v1", "v1"),
                "/**",
                "/actuator/**"
        );
    }

    @Test
    @DisplayName("WebSocket 경로가 아니면 토큰 검증 없이 통과한다")
    void filter_shouldPassThrough_whenPathIsNotWebSocket() {
        // given
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/test")
        );

        given(chain.filter(any(ServerWebExchange.class)))
                .willReturn(Mono.empty());

        // when & then
        StepVerifier.create(sut.filter(exchange, chain))
                .verifyComplete();

        verify(jwtDecoder, never()).decode(any());
        verify(chain).filter(exchange);
    }

    @Test
    @DisplayName("/ws로 시작하는 경로는 access_token 인증 대상이다")
    void filter_shouldAuthenticate_whenPathStartsWithWs() {
        // given
        Jwt jwt = Jwt.withTokenValue("valid-token")
                .header("alg", "none")
                .claim("id", "user-1")
                .claim("roles", List.of("ROLE_USER"))
                .build();

        given(jwtDecoder.decode("valid-token"))
                .willReturn(Mono.just(jwt));

        WebFilterChain chain = exchange -> {
            assertThat(exchange.getRequest().getHeaders().getFirst("X-User-Id"))
                    .isEqualTo("user-1");
            return Mono.empty();
        };

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/ws/test-path?access_token=valid-token").build()
        );

        // when & then
        StepVerifier.create(sut.filter(exchange, chain))
                .verifyComplete();

        then(jwtDecoder).should().decode("valid-token");
    }

    @Test
    @DisplayName("OPTIONS 요청은 토큰 검증 없이 통과한다")
    void filter_shouldPassThrough_whenMethodIsOptions() {
        // given
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.options("/ws")
        );

        given(chain.filter(any(ServerWebExchange.class)))
                .willReturn(Mono.empty());

        // when & then
        StepVerifier.create(sut.filter(exchange, chain))
                .verifyComplete();

        verify(jwtDecoder, never()).decode(any());
        verify(chain).filter(exchange);
    }

    @Test
    @DisplayName("WebSocket 요청에 access_token이 없으면 401을 반환한다")
    void filter_shouldReturnUnauthorized_whenAccessTokenMissing() {
        // given
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/ws")
        );

        // when & then
        StepVerifier.create(sut.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        verify(jwtDecoder, never()).decode(any());
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("WebSocket 요청의 access_token이 비어있으면 401을 반환한다")
    void filter_shouldReturnUnauthorized_whenAccessTokenBlank() {
        // given
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/ws?access_token=")
        );

        // when & then
        StepVerifier.create(sut.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        verify(jwtDecoder, never()).decode(any());
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("JWT 검증에 실패하면 401을 반환한다")
    void filter_shouldReturnUnauthorized_whenJwtDecodeFails() {
        // given
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/ws?access_token=" + INVALID_TOKEN)
        );

        given(jwtDecoder.decode(INVALID_TOKEN))
                .willReturn(Mono.error(new JwtException("invalid token")));

        // when & then
        StepVerifier.create(sut.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        verify(jwtDecoder).decode(INVALID_TOKEN);
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("JWT에 id claim이 없으면 401을 반환한다")
    void filter_shouldReturnUnauthorized_whenJwtIdClaimMissing() {
        // given
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/ws?access_token=" + VALID_TOKEN)
        );

        Jwt jwt = jwtWithoutUserId();

        given(jwtDecoder.decode(VALID_TOKEN))
                .willReturn(Mono.just(jwt));

        // when & then
        StepVerifier.create(sut.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        verify(jwtDecoder).decode(VALID_TOKEN);
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("정상 JWT면 X-User-Id 헤더를 추가하고 인증 객체를 SecurityContext에 저장한다")
    void filter_shouldSetUserIdHeaderAndAuthentication_whenJwtValid() {
        // given
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/ws?access_token=" + VALID_TOKEN)
        );

        Jwt jwt = jwtWithUserIdAndRoles();

        AtomicReference<Authentication> authenticationRef = new AtomicReference<>();

        given(jwtDecoder.decode(VALID_TOKEN))
                .willReturn(Mono.just(jwt));

        given(chain.filter(any(ServerWebExchange.class)))
                .willAnswer(invocation ->
                        ReactiveSecurityContextHolder.getContext()
                                .doOnNext(context -> authenticationRef.set(context.getAuthentication()))
                                .then()
                );

        // when & then
        StepVerifier.create(sut.filter(exchange, chain))
                .verifyComplete();

        ArgumentCaptor<ServerWebExchange> exchangeCaptor =
                ArgumentCaptor.forClass(ServerWebExchange.class);

        verify(chain).filter(exchangeCaptor.capture());

        ServerWebExchange capturedExchange = exchangeCaptor.getValue();

        assertThat(capturedExchange.getRequest().getHeaders().getFirst("X-User-Id"))
                .isEqualTo(USER_ID);

        Authentication authentication = authenticationRef.get();

        assertThat(authentication)
                .isInstanceOf(JwtAuthenticationToken.class);

        assertThat(authentication.getName())
                .isEqualTo(USER_ID);

        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_USER");

        assertThat(exchange.getResponse().getStatusCode())
                .isNull();
    }

    @Test
    @DisplayName("roles claim이 없어도 NPE 없이 인증 처리한다")
    void filter_shouldAuthenticateWithoutNpe_whenRolesClaimMissing() {
        // given
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/ws?access_token=" + VALID_TOKEN)
        );

        given(chain.filter(any(ServerWebExchange.class)))
                .willReturn(Mono.empty());

        Jwt jwt = jwtWithUserIdOnly();

        given(jwtDecoder.decode(VALID_TOKEN))
                .willReturn(Mono.just(jwt));

        // when & then
        StepVerifier.create(sut.filter(exchange, chain))
                .verifyComplete();

        ArgumentCaptor<ServerWebExchange> exchangeCaptor =
                ArgumentCaptor.forClass(ServerWebExchange.class);

        verify(chain).filter(exchangeCaptor.capture());

        ServerWebExchange capturedExchange = exchangeCaptor.getValue();

        assertThat(capturedExchange.getRequest().getHeaders().getFirst("X-User-Id"))
                .isEqualTo(USER_ID);
    }

    private Jwt jwtWithUserIdAndRoles() {
        return Jwt.withTokenValue(VALID_TOKEN)
                .header("alg", "none")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("id", USER_ID)
                .claim("roles", List.of("ROLE_USER"))
                .build();
    }

    private Jwt jwtWithUserIdOnly() {
        return Jwt.withTokenValue(VALID_TOKEN)
                .header("alg", "none")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("id", USER_ID)
                .build();
    }

    private Jwt jwtWithoutUserId() {
        return Jwt.withTokenValue(VALID_TOKEN)
                .header("alg", "none")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("roles", List.of("ROLE_USER"))
                .build();
    }
}
