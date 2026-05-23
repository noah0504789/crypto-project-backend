package endpoint;

import config.*;
import org.example.common.config.MessageConverterConfig;
import org.example.test.config.TestBootApplication;
import org.example.gateway.config.ReactiveSecurityConfig;
import org.example.gateway.filter.IdentityPropagationGlobalFilter;
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
                MessageConverterConfig.class,
                ReactiveSecurityConfig.class,
                IdentityPropagationGlobalFilter.class,

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
    @DisplayName("GET /user/me - 토큰이 없으면 401을 반환한다")
    void userMe_shouldReturnUnauthorized_whenTokenMissing() {
        webTestClient.get()
                .uri("/user/me")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("GET /user/me - ROLE_USER가 없으면 403 JSON 에러 응답을 반환한다")
    void userMe_shouldReturnForbiddenErrorBody_whenRoleMissing() {
        webTestClient.get()
                .uri("/user/me")
                .headers(headers -> headers.setBearerAuth("no-role-token"))
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(403)
                .jsonPath("$.error").isEqualTo("FORBIDDEN")
                .jsonPath("$.message").isEqualTo("Authorization required.")
                .jsonPath("$.path").isEqualTo("/user/me")
                .jsonPath("$.timestamp").exists();
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
}
