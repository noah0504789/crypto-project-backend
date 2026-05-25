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
class IdentityPropagationE2ETest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private TestDownstreamServerConfig.TestDownstreamServers downstreamServers;

    @BeforeEach
    void setUp() {
        downstreamServers.reset();
    }

    @Test
    @DisplayName("JWT id claim이 있으면 X-User-Id 헤더로 downstream에 전파된다")
    void shouldPropagateUserIdHeader_whenJwtHasIdClaim() {
        webTestClient.get()
                .uri("/user/me")
                .headers(headers -> headers.setBearerAuth("user-token"))
                .exchange()
                .expectStatus().isOk();

        TestDownstreamServerConfig.TestDownstreamServers.CapturedRequest request =
                downstreamServers.lastUserRequest();

        assertThat(request).isNotNull();
        assertThat(request.xUserId()).isEqualTo("user-1");
    }

    @Test
    @DisplayName("JWT id claim이 없으면 X-User-Id 헤더를 전파하지 않는다")
    void shouldNotPropagateUserIdHeader_whenJwtHasNoIdClaim() {
        webTestClient.get()
                .uri("/user/me")
                .headers(headers -> headers.setBearerAuth("no-id-token"))
                .exchange()
                .expectStatus().isOk();

        TestDownstreamServerConfig.TestDownstreamServers.CapturedRequest request =
                downstreamServers.lastUserRequest();

        assertThat(request).isNotNull();
        assertThat(request.xUserId()).isNull();
    }
}
