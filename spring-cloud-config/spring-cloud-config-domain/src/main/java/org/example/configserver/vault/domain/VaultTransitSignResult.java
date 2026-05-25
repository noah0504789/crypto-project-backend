package org.example.configserver.vault.domain;

public record VaultTransitSignResult(
        String signature,
        String keyVersion
) {
}