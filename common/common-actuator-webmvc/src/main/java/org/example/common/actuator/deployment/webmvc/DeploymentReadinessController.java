package org.example.common.actuator.deployment.webmvc;

import java.util.Map;

import org.example.common.actuator.deployment.core.DeploymentReadiness;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/deployment")
public class DeploymentReadinessController {

    private final DeploymentReadiness readiness;

    public DeploymentReadinessController(DeploymentReadiness readiness) {
        this.readiness = readiness;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of(
                "ready", readiness.isReady(),
                "updatedAt", readiness.updatedAt().toString()
        );
    }

    @PostMapping("/ready")
    public Map<String, Object> ready() {
        readiness.markReady();

        return Map.of(
                "ready", true,
                "updatedAt", readiness.updatedAt().toString()
        );
    }

    @PostMapping("/not-ready")
    public Map<String, Object> notReady() {
        readiness.markNotReady();

        return Map.of(
                "ready", false,
                "updatedAt", readiness.updatedAt().toString()
        );
    }
}