package org.example.vault;

public interface VaultTransitSignPort {

    VaultTransitSignResult sign(String keyName, Integer keyVersion, String digestB64);
}
