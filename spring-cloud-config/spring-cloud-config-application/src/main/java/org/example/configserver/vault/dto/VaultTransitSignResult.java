package org.example.configserver.vault.dto;

public record VaultTransitSignResult(
        String signature,
        String keyVersion
) {
}