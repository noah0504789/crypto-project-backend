package org.example.jwks;

import lombok.RequiredArgsConstructor;
import org.example.vault.port.out.VaultTransitKeyReadPort;
import org.example.vault.VaultTransitPublicKeyInfo;
import org.springframework.stereotype.Service;

import java.security.interfaces.RSAPublicKey;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class JwksService {

    private final VaultTransitKeyReadPort vaultTransitKeyReadPort;
    private final RsaPublicKeyParser rsaPublicKeyParser;
    private final JwkSetFactory jwkSetFactory;

    public Map<String, Object> getJwks(String keyName) {
        VaultTransitPublicKeyInfo transitKey = vaultTransitKeyReadPort.readLatestKey(keyName);

        RSAPublicKey publicKey = rsaPublicKeyParser.parse(transitKey.publicKeyPem());

        return jwkSetFactory.create(
                publicKey,
                transitKey.kid()
        );
    }
}
