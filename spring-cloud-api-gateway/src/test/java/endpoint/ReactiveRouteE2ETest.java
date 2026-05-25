package endpoint;

import config.*;
import org.example.common.config.MessageConverterConfig;
import org.example.common.test.config.TestBootApplication;
import org.example.gateway.config.ReactiveSecurityConfig;
import org.example.gateway.filter.IdentityPropagationGlobalFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
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
class ReactiveRouteE2ETest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private TestDownstreamServerConfig.TestDownstreamServers downstreamServers;

    @BeforeEach
    void setUp() {
        downstreamServers.reset();
    }

    @Test
    @DisplayName("GET /user/me - user-service로 rewrite되어 라우팅된다")
    void userMe_shouldRewriteAndRouteToUserService() {
        webTestClient.get()
                .uri("/user/me")
                .headers(headers -> headers.setBearerAuth("user-token"))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Gateway", "reactive");

        TestDownstreamServerConfig.TestDownstreamServers.CapturedRequest request =
                downstreamServers.lastUserRequest();

        assertThat(request).isNotNull();
        assertThat(request.method()).isEqualTo("GET");
        assertThat(request.path()).isEqualTo("/api/v1/user/me");
        assertThat(request.xFrom()).isEqualTo("gateway");
    }

    @Test
    @DisplayName("GET /chat/rooms/me - chat-service로 rewrite되어 라우팅된다")
    void chatRoomsMe_shouldRewriteAndRouteToChatService() {
        webTestClient.get()
                .uri("/chat/rooms/me")
                .headers(headers -> headers.setBearerAuth("user-token"))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Gateway", "reactive");

        TestDownstreamServerConfig.TestDownstreamServers.CapturedRequest request =
                downstreamServers.lastChatRequest();

        assertThat(request).isNotNull();
        assertThat(request.method()).isEqualTo("GET");
        assertThat(request.path()).isEqualTo("/api/v1/chat/rooms/me");
        assertThat(request.xFrom()).isEqualTo("gateway");
    }

    @Test
    @DisplayName("POST /auth/logout - oauth2-client로 라우팅된다")
    void authLogout_shouldRouteToOauth2Client() {
        webTestClient.post()
                .uri("/auth/logout")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Gateway", "reactive");

        TestDownstreamServerConfig.TestDownstreamServers.CapturedRequest request =
                downstreamServers.lastOauth2ClientRequest();

        assertThat(request).isNotNull();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo("/auth/logout");
        assertThat(request.xFrom()).isEqualTo("gateway");
    }
}
