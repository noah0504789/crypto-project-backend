package org.example.common.actuator.deployment.core;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "deployment.control")
public record DeploymentControlProperties(
        String token
) {
}