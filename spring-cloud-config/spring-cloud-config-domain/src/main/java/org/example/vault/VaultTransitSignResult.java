package org.example.vault;

public record VaultTransitSignResult(
        String signature,
        String keyVersion
) {
}