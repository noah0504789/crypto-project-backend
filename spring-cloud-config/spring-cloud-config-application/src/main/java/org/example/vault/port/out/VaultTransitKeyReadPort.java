package org.example.vault.port.out;

import org.example.vault.VaultTransitPublicKeyInfo;

public interface VaultTransitKeyReadPort {

    VaultTransitPublicKeyInfo readLatestKey(String keyName);
}
