package org.example.common.actuator.deployment.core;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("deploymentReadiness")
public class DeploymentReadinessHealthIndicator implements HealthIndicator {

    private final DeploymentReadiness readiness;

    public DeploymentReadinessHealthIndicator(DeploymentReadiness readiness) {
        this.readiness = readiness;
    }

    @Override
    public Health health() {
        if (readiness.isReady()) {
            return Health.up()
                    .withDetail("deploymentReady", true)
                    .withDetail("updatedAt", readiness.updatedAt().toString())
                    .build();
        }

        return Health.outOfService()
                .withDetail("deploymentReady", false)
                .withDetail("reason", "Deployment readiness is not approved yet")
                .withDetail("updatedAt", readiness.updatedAt().toString())
                .build();
    }
}