package org.example.apigateway.endpoint;

import org.example.apigateway.config.*;
import org.example.common.test.config.TestBootApplication;
import org.example.apigateway.filter.IdentityPropagationGlobalFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

@SpringBootTest(
        classes = {
                TestBootApplication.class,
                ReactiveSecurityConfig.class,
                IdentityPropagationGlobalFilter.class,

                TestWebFluxObjectMapperConfig.class,
                TestGatewayJwtConfig.class,
                TestGatewayCorsConfig.class,
                TestGatewayRouteConfig.class,
                TestPropertiesConfig.class,
                TestDownstreamServerConfig.class,
                TestLoadBalancerConfig.class
        },
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@EnableAutoConfiguration
@AutoConfigureWebTestClient
class ReactiveSecurityE2ETest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private TestDownstreamServerConfig.TestDownstreamServers downstreamServers;

    @BeforeEach
    void setUp() {
        downstreamServers.reset();
    }

    @Test
    @DisplayName("GET /user/me/profile - 토큰이 없으면 401을 반환한다")
    void userMe_shouldReturnUnauthorized_whenTokenMissing() {
        webTestClient.get()
                .uri("/user/me/profile")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("WWW-Authenticate", "Bearer")
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.error").isEqualTo("UNAUTHORIZED")
                .jsonPath("$.message").isEqualTo("Authentication is required")
                .jsonPath("$.path").isEqualTo("/user/me/profile")
                .jsonPath("$.timestamp").exists();
    }

    @Test
    @DisplayName("GET /user/me/profile - ROLE_USER가 없으면 403 JSON 에러 응답을 반환한다")
    void userMe_shouldReturnForbiddenErrorBody_whenRoleMissing() {
        webTestClient.get()
                .uri("/user/me/profile")
                .headers(headers -> headers.setBearerAuth("no-role-token"))
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(403)
                .jsonPath("$.error").isEqualTo("FORBIDDEN")
                .jsonPath("$.message").isEqualTo("Access is denied")
                .jsonPath("$.path").isEqualTo("/user/me/profile")
                .jsonPath("$.timestamp").exists();
    }

    @Test
    @DisplayName("PATCH /user/me/profile - 토큰이 없으면 401을 반환한다")
    void patchUserMe_shouldReturnUnauthorized_whenTokenMissing() {
        webTestClient.patch()
                .uri("/user/me/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"nickname\":\"noah\"}")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("PATCH /user/me/profile - ROLE_USER가 없으면 403 JSON 에러 응답을 반환한다")
    void patchUserMe_shouldReturnForbiddenErrorBody_whenRoleMissing() {
        webTestClient.patch()
                .uri("/user/me/profile")
                .headers(headers -> headers.setBearerAuth("no-role-token"))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"nickname\":\"noah\"}")
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(403)
                .jsonPath("$.error").isEqualTo("FORBIDDEN")
                .jsonPath("$.message").isEqualTo("Access is denied")
                .jsonPath("$.path").isEqualTo("/user/me/profile");
    }

    @Test
    @DisplayName("POST /auth/logout - 인증 없이 oauth2-client로 라우팅된다")
    void authLogout_shouldBePermitAll() {
        webTestClient.post()
                .uri("/auth/logout")
                .exchange()
                .expectStatus().isOk();

        assertThat(downstreamServers.lastOauth2ClientRequest()).isNotNull();
        assertThat(downstreamServers.lastOauth2ClientRequest().method()).isEqualTo("POST");
        assertThat(downstreamServers.lastOauth2ClientRequest().path()).isEqualTo("/auth/logout");
    }

    @Test
    @DisplayName("GET /price-alerts/me - 토큰이 없으면 401을 반환한다")
    void priceAlertsMe_shouldReturnUnauthorized_whenTokenMissing() {
        webTestClient.get()
                .uri("/price-alerts/me")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("GET /price-alerts/me - ROLE_USER가 없으면 403을 반환한다")
    void priceAlertsMe_shouldReturnForbidden_whenRoleMissing() {
        webTestClient.get()
                .uri("/price-alerts/me")
                .headers(headers -> headers.setBearerAuth("no-role-token"))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    @DisplayName("GET /notifications/me - 토큰이 없으면 401을 반환한다")
    void notificationsMe_shouldReturnUnauthorized_whenTokenMissing() {
        webTestClient.get()
                .uri("/notifications/me")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("GET /notifications/me - ROLE_USER가 없으면 403을 반환한다")
    void notificationsMe_shouldReturnForbidden_whenRoleMissing() {
        webTestClient.get()
                .uri("/notifications/me")
                .headers(headers -> headers.setBearerAuth("no-role-token"))
                .exchange()
                .expectStatus().isForbidden();
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("protectedRouteContracts")
    @DisplayName("Route 계약의 인증 필요 경로는 토큰이 없으면 401을 반환한다")
    void protectedRoutes_shouldReturnUnauthorized_whenTokenMissing(HttpMethod method, String path) {
        webTestClient.method(method)
                .uri(path)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("publicRouteContracts")
    @DisplayName("Route 계약의 공개 경로는 인증 없이 Security chain을 통과한다")
    void publicRoutes_shouldPassSecurityChain_withoutToken(HttpMethod method, String path) {
        webTestClient.method(method)
                .uri(path)
                .exchange()
                .expectStatus()
                .value(status -> assertThat(status).isNotIn(401, 403));
    }

    private static Stream<Arguments> protectedRouteContracts() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, "/user/me/profile"),
                Arguments.of(HttpMethod.GET, "/user/user-1/profile"),
                Arguments.of(HttpMethod.PATCH, "/user/me/profile"),
                Arguments.of(HttpMethod.GET, "/ws-sockjs/websocket"),
                Arguments.of(HttpMethod.GET, "/ws-native"),
                Arguments.of(HttpMethod.GET, "/chat/rooms/me"),
                Arguments.of(HttpMethod.GET, "/chat/room/room-1/me"),
                Arguments.of(HttpMethod.POST, "/chat/room/room-1/members"),
                Arguments.of(HttpMethod.DELETE, "/chat/room/room-1/members"),
                Arguments.of(HttpMethod.PUT, "/chat/room/room-1/activity"),
                Arguments.of(HttpMethod.GET, "/chat/room/room-1/messages"),
                Arguments.of(HttpMethod.POST, "/chat/room"),
                Arguments.of(HttpMethod.GET, "/chat/room/room-1"),
                Arguments.of(HttpMethod.PATCH, "/chat/room/room-1"),
                Arguments.of(HttpMethod.DELETE, "/chat/room/room-1"),
                Arguments.of(HttpMethod.GET, "/price-alerts/me"),
                Arguments.of(HttpMethod.PUT, "/price-alerts/me"),
                Arguments.of(HttpMethod.GET, "/notifications/me"),
                Arguments.of(HttpMethod.PATCH, "/notifications/notification-1/read")
        );
    }

    private static Stream<Arguments> publicRouteContracts() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, "/oauth2/authorization/google"),
                Arguments.of(HttpMethod.GET, "/login/oauth2/code/google"),
                Arguments.of(HttpMethod.POST, "/auth/refresh"),
                Arguments.of(HttpMethod.POST, "/auth/logout"),
                Arguments.of(HttpMethod.POST, "/user/sign-up"),
                Arguments.of(HttpMethod.GET, "/chat/rooms/popular"),
                Arguments.of(HttpMethod.GET, "/markets"),
                Arguments.of(HttpMethod.GET, "/ws-sockjs/info"),
                Arguments.of(HttpMethod.POST, "/msg/chat.send")
        );
    }
}
