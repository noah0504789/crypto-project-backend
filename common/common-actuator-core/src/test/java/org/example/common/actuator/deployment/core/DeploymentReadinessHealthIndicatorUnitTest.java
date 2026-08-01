package org.example.common.actuator.deployment.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

class DeploymentReadinessHealthIndicatorUnitTest {

    @Test
    @DisplayName("not-ready 상태이면 OUT_OF_SERVICE를 반환한다")
    void shouldReturnOutOfServiceWhenNotReady() {
        DeploymentReadiness readiness = new DeploymentReadiness();
        DeploymentReadinessHealthIndicator sut = new DeploymentReadinessHealthIndicator(readiness);

        Health health = sut.health();

        assertThat(health.getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
        assertThat(health.getDetails())
                .containsEntry("deploymentReady", false)
                .containsKey("reason")
                .containsKey("updatedAt");
    }

    @Test
    @DisplayName("ready 상태이면 UP을 반환한다")
    void shouldReturnUpWhenReady() {
        DeploymentReadiness readiness = new DeploymentReadiness();
        DeploymentReadinessHealthIndicator sut = new DeploymentReadinessHealthIndicator(readiness);
        readiness.markReady();


        Health health = sut.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails())
                .containsEntry("deploymentReady", true)
                .containsKey("updatedAt");
    }
}