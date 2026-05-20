package org.example.common.id.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Instant;

@ConfigurationProperties(prefix = "idgen")
public record IdGenProperties(
        Instant epoch,
        int datacenterId,
        int workerId
) {
}
