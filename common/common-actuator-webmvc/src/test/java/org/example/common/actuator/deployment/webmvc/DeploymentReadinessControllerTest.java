package org.example.common.actuator.deployment.webmvc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.example.common.actuator.deployment.core.DeploymentReadiness;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DeploymentReadinessControllerTest {

    @Test
    @DisplayName("현재 배포 readiness 상태를 조회한다")
    void shouldReturnCurrentStatus() {
        DeploymentReadiness readiness = new DeploymentReadiness();
        DeploymentReadinessController sut = new DeploymentReadinessController(readiness);

        Map<String, Object> response = sut.status();

        assertThat(response)
                .containsEntry("ready", false)
                .containsKey("updatedAt");
    }

    @Test
    @DisplayName("ready API 호출 시 ready 상태로 전환한다")
    void shouldMarkReady() {
        DeploymentReadiness readiness = new DeploymentReadiness();
        DeploymentReadinessController sut = new DeploymentReadinessController(readiness);

        Map<String, Object> response = sut.ready();

        assertThat(readiness.isReady()).isTrue();
        assertThat(response)
                .containsEntry("ready", true)
                .containsKey("updatedAt");
    }

    @Test
    @DisplayName("not-ready API 호출 시 not-ready 상태로 전환한다")
    void shouldMarkNotReady() {
        DeploymentReadiness readiness = new DeploymentReadiness();
        readiness.markReady();

        DeploymentReadinessController sut = new DeploymentReadinessController(readiness);

        Map<String, Object> response = sut.notReady();

        assertThat(readiness.isReady()).isFalse();
        assertThat(response)
                .containsEntry("ready", false)
                .containsKey("updatedAt");
    }
}