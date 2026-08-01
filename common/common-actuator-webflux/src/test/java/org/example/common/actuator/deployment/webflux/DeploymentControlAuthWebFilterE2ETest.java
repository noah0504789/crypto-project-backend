package org.example.common.actuator.deployment.webflux;

import org.example.common.actuator.deployment.core.DeploymentControlProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

class DeploymentControlAuthWebFilterTest {

    private static final String TOKEN = "test-deploy-token";

    @Test
    @DisplayName("배포 제어 경로가 아니면 토큰 없이도 통과한다")
    void pass_when_path_is_not_deployment_control_path() {
        // given
        WebTestClient client = webTestClient(new DeploymentControlAuthWebFilter(
                new DeploymentControlProperties(TOKEN)
        ));

        // when & then
        client.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo("OK");
    }

    @Test
    @DisplayName("배포 제어 경로에서 올바른 토큰이면 통과한다")
    void pass_when_deployment_token_is_valid() {
        // given
        WebTestClient client = webTestClient(new DeploymentControlAuthWebFilter(
                new DeploymentControlProperties(TOKEN)
        ));

        // when & then
        client.post()
                .uri("/internal/deployment/ready")
                .header("X-Deploy-Token", TOKEN)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo("OK");
    }

    @Test
    @DisplayName("배포 제어 경로에서 토큰이 없으면 401을 반환한다")
    void reject_when_deployment_token_is_missing() {
        // given
        WebTestClient client = webTestClient(new DeploymentControlAuthWebFilter(
                new DeploymentControlProperties(TOKEN)
        ));

        // when & then
        client.post()
                .uri("/internal/deployment/ready")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentType("application/json")
                .expectBody()
                .jsonPath("$.message")
                .isEqualTo("Unauthorized deployment control request");
    }

    @Test
    @DisplayName("배포 제어 경로에서 토큰이 틀리면 401을 반환한다")
    void reject_when_deployment_token_is_invalid() {
        // given
        WebTestClient client = webTestClient(new DeploymentControlAuthWebFilter(
                new DeploymentControlProperties(TOKEN)
        ));

        // when & then
        client.post()
                .uri("/internal/deployment/not-ready")
                .header("X-Deploy-Token", "wrong-token")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentType("application/json")
                .expectBody()
                .jsonPath("$.message")
                .isEqualTo("Unauthorized deployment control request");
    }

    private WebTestClient webTestClient(WebFilter filter) {
        return WebTestClient
                .bindToWebHandler(exchange -> {
                    exchange.getResponse().getHeaders().add(HttpHeaders.CONTENT_TYPE, "text/plain");
                    byte[] body = "OK".getBytes();
                    return exchange.getResponse().writeWith(
                            Mono.just(exchange.getResponse().bufferFactory().wrap(body))
                    );
                })
                .webFilter(filter)
                .build();
    }
}