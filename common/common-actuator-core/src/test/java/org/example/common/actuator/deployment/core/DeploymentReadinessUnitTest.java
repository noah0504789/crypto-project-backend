package org.example.common.actuator.deployment.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DeploymentReadinessUnitTest {

    @Test
    @DisplayName("기본 상태는 not ready 이다")
    void shouldBeNotReadyByDefault() {
        DeploymentReadiness sut = new DeploymentReadiness();

        assertThat(sut.isReady()).isFalse();
        assertThat(sut.updatedAt()).isNotNull();
    }

    @Test
    @DisplayName("ready 상태로 전환할 수 있다")
    void shouldMarkReady() {
        DeploymentReadiness sut = new DeploymentReadiness();

        sut.markReady();

        assertThat(sut.isReady()).isTrue();
        assertThat(sut.updatedAt()).isNotNull();
    }

    @Test
    @DisplayName("not-ready 상태로 다시 전환할 수 있다")
    void shouldMarkNotReady() {
        DeploymentReadiness sut = new DeploymentReadiness();

        sut.markReady();
        sut.markNotReady();

        assertThat(sut.isReady()).isFalse();
        assertThat(sut.updatedAt()).isNotNull();
    }
}