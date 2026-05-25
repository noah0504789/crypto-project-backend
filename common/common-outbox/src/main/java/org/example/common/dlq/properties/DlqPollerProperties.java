package org.example.common.dlq.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "poller.dlq")
public record DlqPollerProperties(
        boolean enabled,
        int fixedDelayMs,
        int batchSize
) {
}