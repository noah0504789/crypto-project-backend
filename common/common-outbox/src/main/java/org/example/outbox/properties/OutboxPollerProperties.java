package org.example.outbox.properties;

import org.example.outbox.domain.OutboxDispatchType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "poller.outbox")
public record OutboxPollerProperties(
        @DefaultValue Item general,
        @DefaultValue Item broadcast
) {

    public Item get(OutboxDispatchType dispatchType) {
        return switch (dispatchType) {
            case GENERAL -> general;
            case BROADCAST -> broadcast;
        };
    }

    public record Item(
            boolean enabled,
            int fixedDelayMs,
            int batchSize,
            int maxRetryCnt
    ) {
    }
}