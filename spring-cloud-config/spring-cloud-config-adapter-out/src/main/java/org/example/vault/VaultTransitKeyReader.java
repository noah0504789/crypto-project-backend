package org.example.vault;

import lombok.RequiredArgsConstructor;
import org.example.common.exception.ConfigInfrastructureException;
import org.example.common.exception.VaultKeyNotFoundException;
import org.example.infra.properties.VaultTransitProperties;
import org.example.vault.VaultTransitKeyReadPort;
import org.springframework.stereotype.Component;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class VaultTransitKeyReader implements VaultTransitKeyReadPort {

    private final VaultTemplate vaultTemplate;
    private final VaultTransitProperties vaultTransitProperties;

    public VaultTransitPublicKeyInfo readLatestKey(String keyName) {
        VaultResponse response = vaultTemplate.read(vaultTransitProperties.keyPathPrefix() + keyName);

        if (response == null || response.getData() == null) {
            throw new VaultKeyNotFoundException("Vault key response is empty. keyName=" + keyName);
        }

        Map<String, Object> data = response.getData();

        String latestVersion = String.valueOf(data.get("latest_version"));

        Map<String, Object> keys = castMap(data.get("keys"), "keys");
        Map<String, Object> meta = castMap(keys.get(latestVersion), "keys." + latestVersion);

        String publicKeyPem = String.valueOf(meta.get("public_key"));

        return new VaultTransitPublicKeyInfo(
                keyName,
                latestVersion,
                publicKeyPem
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value, String fieldName) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new ConfigInfrastructureException("Vault field is not map. field=" + fieldName);
        }

        return (Map<String, Object>) map;
    }
}
