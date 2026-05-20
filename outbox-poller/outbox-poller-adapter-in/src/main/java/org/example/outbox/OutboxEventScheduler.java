package org.example.outbox;

import lombok.RequiredArgsConstructor;
import org.example.outbox.properties.OutboxPollerProperties;
import org.example.outbox.domain.OutboxDispatchType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OutboxEventScheduler {

    private final OutboxService outboxPollerService;
    private final OutboxPollerProperties outboxPollerProperties;

    @Scheduled(fixedDelayString = "#{@outboxPollerProperties.general.fixedDelayMs}")
    public void pollGeneral() {
        if (!outboxPollerProperties.general().enabled()) return;

        outboxPollerService.publishPending(OutboxDispatchType.GENERAL);
    }

    @Scheduled(fixedDelayString = "#{@outboxPollerProperties.broadcast.fixedDelayMs}")
    public void pollBroadcast() {
        if (!outboxPollerProperties.broadcast().enabled()) return;

        outboxPollerService.publishPending(OutboxDispatchType.BROADCAST);
    }
}
