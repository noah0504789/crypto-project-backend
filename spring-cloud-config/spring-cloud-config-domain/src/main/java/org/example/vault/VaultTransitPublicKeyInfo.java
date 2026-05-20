package org.example.vault;

public record VaultTransitPublicKeyInfo(
        String keyName,
        String version,
        String publicKeyPem
) {
    public String kid() {
        return keyName + ":" + version;
    }
}