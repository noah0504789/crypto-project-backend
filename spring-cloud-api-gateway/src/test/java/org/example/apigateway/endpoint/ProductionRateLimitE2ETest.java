package org.example.apigateway.endpoint;

import org.example.apigateway.config.ReactiveRouteConfig;
import org.example.apigateway.config.ReactiveSecurityConfig;
import org.example.apigateway.config.TestDownstreamServerConfig;
import org.example.apigateway.config.TestGatewayCorsConfig;
import org.example.apigateway.config.TestGatewayJwtConfig;
import org.example.apigateway.config.TestLoadBalancerClientFactoryConfig;
import org.example.apigateway.config.TestPropertiesConfig;
import org.example.apigateway.config.TestWebFluxObjectMapperConfig;
import org.example.apigateway.filter.IdentityPropagationGlobalFilter;
import org.example.apigateway.ratelimit.RateLimitConfig;
import org.example.common.test.config.TestBootApplication;
import org.example.common.test.testcontainer.RedisTestContainerInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = {
                TestBootApplication.class,
                ReactiveSecurityConfig.class,
                IdentityPropagationGlobalFilter.class,
                ReactiveRouteConfig.class,
                RateLimitConfig.class,
                TestWebFluxObjectMapperConfig.class,
                TestGatewayJwtConfig.class,
                TestGatewayCorsConfig.class,
                TestPropertiesConfig.class,
                TestDownstreamServerConfig.class,
                TestLoadBalancerClientFactoryConfig.class
        },
        properties = {
                "gateway.rate-limit.command.replenish-rate=1",
                "gateway.rate-limit.command.burst-capacity=120",
                "gateway.rate-limit.command.requested-tokens=60"
        },
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@EnableAutoConfiguration
@AutoConfigureWebTestClient
@ContextConfiguration(initializers = RedisTestContainerInitializer.class)
class ProductionRateLimitE2ETest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private TestDownstreamServerConfig.TestDownstreamServers downstreamServers;

    @BeforeEach
    void setUp() {
        downstreamServers.reset();
    }

    @Test
    @DisplayName("Production 프로필 Command Route는 순간 두 요청 후 429와 Rate Limit Header를 반환한다")
    void profileCommandRoute_shouldReturn429WithRateLimitHeaders() {
        requestProfileUpdate()
                .expectStatus().isOk()
                .expectHeader().exists("X-RateLimit-Remaining")
                .expectHeader().valueEquals("X-RateLimit-Replenish-Rate", "1")
                .expectHeader().valueEquals("X-RateLimit-Burst-Capacity", "120")
                .expectHeader().valueEquals("X-RateLimit-Requested-Tokens", "60");

        requestProfileUpdate()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-RateLimit-Remaining", "0");

        requestProfileUpdate()
                .expectStatus().isEqualTo(429)
                .expectHeader().valueEquals("X-RateLimit-Remaining", "0")
                .expectHeader().valueEquals("X-RateLimit-Replenish-Rate", "1")
                .expectHeader().valueEquals("X-RateLimit-Burst-Capacity", "120")
                .expectHeader().valueEquals("X-RateLimit-Requested-Tokens", "60");

        TestDownstreamServerConfig.TestDownstreamServers.CapturedRequest request =
                downstreamServers.lastUserRequest();
        assertThat(request).isNotNull();
        assertThat(request.path()).isEqualTo("/api/v1/user/me/profile");
        assertThat(downstreamServers.userRequestCount()).isEqualTo(2);
    }

    private WebTestClient.ResponseSpec requestProfileUpdate() {
        return webTestClient.patch()
                .uri("/user/me/profile")
                .headers(headers -> headers.setBearerAuth("user-token"))
                .exchange();
    }
}
