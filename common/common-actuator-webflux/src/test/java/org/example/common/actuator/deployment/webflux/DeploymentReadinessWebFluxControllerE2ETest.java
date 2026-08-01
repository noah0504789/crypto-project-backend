package org.example.common.actuator.deployment.webflux;

import org.example.common.actuator.deployment.core.DeploymentReadiness;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

class DeploymentReadinessWebFluxControllerE2ETest {

    @Test
    @DisplayName("현재 배포 readiness 상태를 조회한다")
    void get_status() {
        // given
        DeploymentReadiness readiness = new DeploymentReadiness();
        DeploymentReadinessWebFluxController controller =
                new DeploymentReadinessWebFluxController(readiness);

        WebTestClient client = WebTestClient
                .bindToController(controller)
                .build();

        // when & then
        client.get()
                .uri("/internal/deployment/status")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.ready").isEqualTo(readiness.isReady())
                .jsonPath("$.updatedAt").exists();
    }

    @Test
    @DisplayName("ready 요청 시 readiness 상태를 true로 변경한다")
    void mark_ready() {
        // given
        DeploymentReadiness readiness = new DeploymentReadiness();
        readiness.markNotReady();

        DeploymentReadinessWebFluxController controller =
                new DeploymentReadinessWebFluxController(readiness);

        WebTestClient client = WebTestClient
                .bindToController(controller)
                .build();

        // when & then
        client.post()
                .uri("/internal/deployment/ready")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.ready").isEqualTo(true)
                .jsonPath("$.updatedAt").exists();

        // then
        assert readiness.isReady();
    }

    @Test
    @DisplayName("not-ready 요청 시 readiness 상태를 false로 변경한다")
    void mark_not_ready() {
        // given
        DeploymentReadiness readiness = new DeploymentReadiness();
        readiness.markReady();

        DeploymentReadinessWebFluxController controller =
                new DeploymentReadinessWebFluxController(readiness);

        WebTestClient client = WebTestClient
                .bindToController(controller)
                .build();

        // when & then
        client.post()
                .uri("/internal/deployment/not-ready")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.ready").isEqualTo(false)
                .jsonPath("$.updatedAt").exists();

        // then
        assert !readiness.isReady();
    }
}