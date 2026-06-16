package org.example.outboxpoller.outbox;

import lombok.RequiredArgsConstructor;
import org.example.common.outbox.application.service.OutboxService;
import org.example.common.outbox.properties.OutboxPollerProperties;
import org.example.common.outbox.domain.OutboxDispatchType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OutboxEventScheduler {

    private final OutboxService outboxPollerService;
    private final OutboxPollerProperties outboxPollerProperties;

    @Scheduled(fixedDelayString = "${poller.outbox.general.fixed-delay-ms}")
    public void pollGeneral() {
        if (!outboxPollerProperties.general().enabled()) return;

        outboxPollerService.publishPending(OutboxDispatchType.GENERAL);
    }

    @Scheduled(fixedDelayString = "${poller.outbox.broadcast.fixed-delay-ms}")
    public void pollBroadcast() {
        if (!outboxPollerProperties.broadcast().enabled()) return;

        outboxPollerService.publishPending(OutboxDispatchType.BROADCAST);
    }
}
