package org.example.vault.adapter.out;

import lombok.RequiredArgsConstructor;
import org.example.vault.adapter.out.properties.VaultTransitProperties;
import org.example.vault.adapter.out.dto.VaultSignRequest;
import org.example.vault.port.out.VaultTransitSignPort;
import org.example.vault.VaultTransitSignResult;
import org.springframework.stereotype.Component;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class VaultTransitSigner implements VaultTransitSignPort {

    private final VaultTemplate vaultTemplate;
    private final VaultTransitProperties vaultTransitProperties;

    public VaultTransitSignResult sign(String keyName, Integer keyVersion, String digestB64) {
        VaultSignRequest transitRequest = new VaultSignRequest(
                digestB64,
                true,
                "sha2-256",
                "pkcs1v15",
                keyVersion
        );

        VaultResponse response = vaultTemplate.write(
                vaultTransitProperties.signPathPrefix() + keyName,
                transitRequest
        );

        if (response == null || response.getData() == null) {
            throw new IllegalStateException("Vault sign response is empty. keyName=" + keyName);
        }

        Map<String, Object> data = response.getData();

        String signature = String.valueOf(data.get("signature"));
        String keyVersionFromVault = String.valueOf(data.get("key_version"));

        return new VaultTransitSignResult(
                signature,
                keyVersionFromVault
        );
    }
}
