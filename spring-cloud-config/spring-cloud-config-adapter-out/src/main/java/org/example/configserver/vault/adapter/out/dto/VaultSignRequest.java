package org.example.configserver.vault.adapter.out.dto;

public record VaultSignRequest(
        String input,
        boolean prehashed,
        String hash_algorithm,
        String signature_algorithm,
        Integer key_version
        ) {
}
