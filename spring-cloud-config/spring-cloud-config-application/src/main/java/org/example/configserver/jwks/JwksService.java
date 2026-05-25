package org.example.configserver.jwks;

import lombok.RequiredArgsConstructor;
import org.example.configserver.vault.port.out.VaultTransitKeyReaderPort;
import org.example.configserver.vault.domain.VaultTransitPublicKeyInfo;
import org.springframework.stereotype.Service;

import java.security.interfaces.RSAPublicKey;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class JwksService {

    private final VaultTransitKeyReaderPort vaultTransitKeyReaderPort;
    private final RsaPublicKeyParser rsaPublicKeyParser;
    private final JwkSetFactory jwkSetFactory;

    public Map<String, Object> getJwks(String keyName) {
        VaultTransitPublicKeyInfo transitKey = vaultTransitKeyReaderPort.readLatestKey(keyName);

        RSAPublicKey publicKey = rsaPublicKeyParser.parse(transitKey.publicKeyPem());

        return jwkSetFactory.create(
                publicKey,
                transitKey.kid()
        );
    }
}
