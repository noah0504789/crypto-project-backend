package org.example.vault;

public interface VaultTransitKeyReadPort {

    VaultTransitPublicKeyInfo readLatestKey(String keyName);
}
