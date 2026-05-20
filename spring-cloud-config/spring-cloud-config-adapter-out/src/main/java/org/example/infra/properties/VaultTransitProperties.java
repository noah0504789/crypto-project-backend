package org.example.infra.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vault.transit")
public record VaultTransitProperties(
        String signPathPrefix,
        String keyPathPrefix
) {
}
