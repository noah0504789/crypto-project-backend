package org.example.outboxpoller.dlq;

import lombok.RequiredArgsConstructor;
import org.example.common.dlq.application.service.DlqService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DlqEventScheduler {

    private final DlqService dlqService;
    private final DlqPollerState dlqPollerState;

    @Scheduled(fixedDelayString = "${poller.dlq.fixed-delay-ms}")
    public void poll() {
        if (!dlqPollerState.isEnabled()) return;

        dlqService.publishPending();
    }
}
