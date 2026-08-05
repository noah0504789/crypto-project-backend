package org.example.outboxpoller.outbox;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.example.common.outbox.domain.OutboxDispatchType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "poller.outbox")
public record OutboxPollerProperties(
        @Valid @NotNull Item general,
        @Valid @NotNull Item broadcast
) {

    public Item get(OutboxDispatchType dispatchType) {
        return switch (dispatchType) {
            case GENERAL -> general;
            case BROADCAST -> broadcast;
        };
    }

    public record Item(
            @NotNull Boolean enabled,
            @Positive Integer fixedDelayMs,
            @Positive Integer batchSize,
            @Positive Integer maxRetryCnt
    ) {
    }
}
