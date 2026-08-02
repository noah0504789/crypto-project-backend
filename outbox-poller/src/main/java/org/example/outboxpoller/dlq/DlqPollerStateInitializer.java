package org.example.outboxpoller.dlq;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DlqPollerStateInitializer {

    private final DlqPollerState dlqPollerState;
    private final DlqPollerProperties dlqPollerProperties;

    @PostConstruct
    public void init() {
        if (dlqPollerProperties.enabled()) {
            dlqPollerState.start();
        } else {
            dlqPollerState.stop();
        }
    }
}
