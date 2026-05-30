package org.example.common.actuator.deployment.core;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class DeploymentReadiness {

    private final AtomicBoolean ready = new AtomicBoolean(false);
    private volatile Instant updatedAt = Instant.now();

    public boolean isReady() {
        return ready.get();
    }

    public void markReady() {
        ready.set(true);
        updatedAt = Instant.now();
    }

    public void markNotReady() {
        ready.set(false);
        updatedAt = Instant.now();
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}