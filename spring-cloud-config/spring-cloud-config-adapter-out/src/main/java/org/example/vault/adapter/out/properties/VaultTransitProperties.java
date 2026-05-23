package org.example.vault.adapter.out.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vault.transit")
public record VaultTransitProperties(
        String signPathPrefix,
        String keyPathPrefix
) {
}
