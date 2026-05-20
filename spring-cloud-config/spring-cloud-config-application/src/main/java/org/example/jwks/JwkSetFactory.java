package org.example.jwks;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.stereotype.Component;

import java.security.interfaces.RSAPublicKey;
import java.util.Map;

@Component
public class JwkSetFactory {

    public Map<String, Object> create(RSAPublicKey publicKey, String kid) {
        RSAKey jwk = new RSAKey.Builder(publicKey)
                .keyID(kid)
                .algorithm(JWSAlgorithm.RS256)
                .keyUse(KeyUse.SIGNATURE)
                .build();

        return new JWKSet(jwk).toJSONObject();
    }
}