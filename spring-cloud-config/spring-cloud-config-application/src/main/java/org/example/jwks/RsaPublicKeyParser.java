package org.example.jwks;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Component
@RequiredArgsConstructor
public class RsaPublicKeyParser {

    private final KeyFactory rsaKeyFactory;
    private final Base64.Decoder base64Decoder = Base64.getDecoder();

    public RSAPublicKey parse(String publicKeyPem) {
        try {
            String base64 = publicKeyPem
                    .replaceAll("-----\\w+ PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");

            byte[] der = base64Decoder.decode(base64);

            return (RSAPublicKey) rsaKeyFactory.generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse RSA public key", e);
        }
    }
}