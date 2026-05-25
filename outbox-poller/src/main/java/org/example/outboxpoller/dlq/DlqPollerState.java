package org.example.outboxpoller.dlq;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class DlqPollerState {

    private final AtomicBoolean enabled = new AtomicBoolean(true);

    public boolean isEnabled() {
        return enabled.get();
    }

    public void start() {
        enabled.set(true);
    }

    public void stop() {
        enabled.set(false);
    }
}
