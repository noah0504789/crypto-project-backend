package org.example.vault.port.out;

import org.example.vault.VaultTransitSignResult;

public interface VaultTransitSignPort {

    VaultTransitSignResult sign(String keyName, Integer keyVersion, String digestB64);
}
