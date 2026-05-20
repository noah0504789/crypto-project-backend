package org.example.sign;

public record VaultSignRequest(
        String input,
        boolean prehashed,
        String hash_algorithm,
        String signature_algorithm,
        Integer key_version
        ) {
}
