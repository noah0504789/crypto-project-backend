package org.example.common.actuator.deployment.webflux;

import org.example.common.actuator.deployment.core.DeploymentReadiness;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/internal/deployment")
public class DeploymentReadinessWebFluxController {

    private final DeploymentReadiness readiness;

    public DeploymentReadinessWebFluxController(DeploymentReadiness readiness) {
        this.readiness = readiness;
    }

    @GetMapping("/status")
    public Mono<Map<String, Object>> status() {
        return Mono.just(Map.of(
                "ready", readiness.isReady(),
                "updatedAt", readiness.updatedAt().toString()
        ));
    }

    @PostMapping("/ready")
    public Mono<Map<String, Object>> ready() {
        readiness.markReady();

        return Mono.just(Map.of(
                "ready", true,
                "updatedAt", readiness.updatedAt().toString()
        ));
    }

    @PostMapping("/not-ready")
    public Mono<Map<String, Object>> notReady() {
        readiness.markNotReady();

        return Mono.just(Map.of(
                "ready", false,
                "updatedAt", readiness.updatedAt().toString()
        ));
    }
}