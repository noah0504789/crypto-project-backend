package filter;

import org.example.gateway.filter.IdentityPropagationGlobalFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class IdentityPropagationGlobalFilterTest {

    private static final String USER_ID_HEADER = "X-User-Id";

    @Mock
    private GatewayFilterChain chain;

    private IdentityPropagationGlobalFilter sut;

    @BeforeEach
    void setUp() {
        sut = new IdentityPropagationGlobalFilter();
    }

    @Test
    @DisplayName("JWT id claim이 있으면 X-User-Id 헤더로 전파한다")
    void shouldPropagateUserIdHeader_whenJwtHasIdClaim() {
        // given
        ServerWebExchange exchange = exchangeWithPrincipal(
                jwtAuthenticationToken(jwt("user-token", Map.of(
                        "sub", "user@test.com",
                        "id", "user-1",
                        "roles", List.of("ROLE_USER")
                )))
        );

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);

        given(chain.filter(any(ServerWebExchange.class)))
                .willReturn(Mono.empty());

        // when
        sut.filter(exchange, chain).block();

        // then
        then(chain).should().filter(captor.capture());

        ServerWebExchange propagatedExchange = captor.getValue();

        assertThat(propagatedExchange.getRequest()
                .getHeaders()
                .getFirst(USER_ID_HEADER))
                .isEqualTo("user-1");
    }

    @Test
    @DisplayName("JWT id claim이 없으면 X-User-Id 헤더를 전파하지 않는다")
    void shouldNotPropagateUserIdHeader_whenJwtHasNoIdClaim() {
        // given
        ServerWebExchange exchange = exchangeWithPrincipal(
                jwtAuthenticationToken(jwt("no-id-token", Map.of(
                        "sub", "user@test.com",
                        "roles", List.of("ROLE_USER")
                )))
        );

        ArgumentCaptor<ServerWebExchange> captor =
                ArgumentCaptor.forClass(ServerWebExchange.class);

        given(chain.filter(any(ServerWebExchange.class)))
                .willReturn(Mono.empty());

        // when
        sut.filter(exchange, chain).block();

        // then
        then(chain).should().filter(captor.capture());

        ServerWebExchange propagatedExchange = captor.getValue();

        assertThat(propagatedExchange.getRequest()
                .getHeaders()
                .getFirst(USER_ID_HEADER))
                .isNull();
    }

    @Test
    @DisplayName("principal이 없으면 원 요청 그대로 downstream으로 전달한다")
    void shouldPassOriginalExchange_whenPrincipalMissing() {
        // given
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/user/me")
        );

        ArgumentCaptor<ServerWebExchange> captor =
                ArgumentCaptor.forClass(ServerWebExchange.class);

        given(chain.filter(any(ServerWebExchange.class)))
                .willReturn(Mono.empty());

        // when
        sut.filter(exchange, chain).block();

        // then
        then(chain).should().filter(captor.capture());

        ServerWebExchange propagatedExchange = captor.getValue();

        assertThat(propagatedExchange.getRequest()
                .getHeaders()
                .getFirst(USER_ID_HEADER))
                .isNull();
    }

    @Test
    @DisplayName("기존 X-User-Id 헤더가 있어도 JWT id claim 값으로 덮어쓴다")
    void shouldOverwriteUserIdHeader_whenHeaderAlreadyExists() {
        // given
        ServerWebExchange exchange = MockServerWebExchange.from(
                        MockServerHttpRequest.get("/user/me")
                                .header(USER_ID_HEADER, "spoofed-user")
                ).mutate()
                .principal(Mono.just(jwtAuthenticationToken(jwt("user-token", Map.of(
                        "sub", "user@test.com",
                        "id", "user-1",
                        "roles", List.of("ROLE_USER")
                )))))
                .build();

        ArgumentCaptor<ServerWebExchange> captor =
                ArgumentCaptor.forClass(ServerWebExchange.class);

        given(chain.filter(any(ServerWebExchange.class)))
                .willReturn(Mono.empty());

        // when
        sut.filter(exchange, chain).block();

        // then
        then(chain).should().filter(captor.capture());

        ServerWebExchange propagatedExchange = captor.getValue();

        assertThat(propagatedExchange.getRequest()
                .getHeaders()
                .getFirst(USER_ID_HEADER))
                .isEqualTo("user-1");
    }

    private ServerWebExchange exchangeWithPrincipal(JwtAuthenticationToken authentication) {
        return MockServerWebExchange.from(
                        MockServerHttpRequest.get("/user/me")
                ).mutate()
                .principal(Mono.just(authentication))
                .build();
    }

    private JwtAuthenticationToken jwtAuthenticationToken(Jwt jwt) {
        List<SimpleGrantedAuthority> authorities =
                jwt.getClaimAsStringList("roles") == null
                        ? List.of()
                        : jwt.getClaimAsStringList("roles")
                          .stream()
                          .map(SimpleGrantedAuthority::new)
                          .toList();

        return new JwtAuthenticationToken(jwt, authorities);
    }

    private Jwt jwt(String tokenValue, Map<String, Object> claims) {
        Instant now = Instant.now();

        return new Jwt(
                tokenValue,
                now,
                now.plusSeconds(3600),
                Map.of(
                        "alg", "none",
                        "typ", "JWT"
                ),
                claims
        );
    }
}