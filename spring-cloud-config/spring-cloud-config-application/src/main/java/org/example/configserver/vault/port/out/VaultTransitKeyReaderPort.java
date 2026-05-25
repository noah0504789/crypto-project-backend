package org.example.configserver.vault.port.out;

import org.example.configserver.vault.domain.VaultTransitPublicKeyInfo;

public interface VaultTransitKeyReaderPort {

    VaultTransitPublicKeyInfo readLatestKey(String keyName);
}
