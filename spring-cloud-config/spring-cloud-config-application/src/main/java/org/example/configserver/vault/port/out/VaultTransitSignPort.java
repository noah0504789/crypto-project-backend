package org.example.configserver.vault.port.out;

import org.example.configserver.vault.domain.VaultTransitSignResult;

public interface VaultTransitSignPort {

    VaultTransitSignResult sign(String keyName, Integer keyVersion, String digestB64);
}
