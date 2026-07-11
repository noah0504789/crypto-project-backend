package org.example.configserver.vault.port.out;

import org.example.configserver.vault.dto.VaultTransitSignResult;

public interface VaultTransitSignPort {

    VaultTransitSignResult sign(String keyName, Integer keyVersion, String digestB64);
}
