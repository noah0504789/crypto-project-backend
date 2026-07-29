package org.example.apigateway.endpoint;

import org.example.apigateway.config.*;
import org.example.common.test.config.TestBootApplication;
import org.example.apigateway.filter.IdentityPropagationGlobalFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

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
                .expectStatus().isUnauthorized();
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
                .jsonPath("$.message").isEqualTo("Authorization required.")
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
}
