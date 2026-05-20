package org.example.sign;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@Component
public class JwtSigningInputDigester {

    private final Base64.Encoder base64Encoder = Base64.getEncoder();

    public String digestToBase64(String headerB64u, String payloadB64u) {
        try {
            String signingInput = headerB64u + "." + payloadB64u;

            byte[] bytes = signingInput.getBytes(StandardCharsets.US_ASCII);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);

            return base64Encoder.encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available", e);
        }
    }
}