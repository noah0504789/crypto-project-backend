package org.example.sign;

import org.springframework.stereotype.Component;

import java.util.Base64;

@Component
public class VaultSignatureParser {

    private final Base64.Decoder base64Decoder = Base64.getDecoder();
    private final Base64.Encoder base64UrlEncoder = Base64.getUrlEncoder().withoutPadding();

    public String toBase64Url(String vaultSignature) {
        if (vaultSignature == null || vaultSignature.isBlank()) {
            throw new IllegalArgumentException("Vault signature is empty");
        }

        int index = vaultSignature.lastIndexOf(':');

        if (index < 0 || index == vaultSignature.length() - 1) {
            throw new IllegalArgumentException("Invalid Vault signature format");
        }

        String base64Signature = vaultSignature.substring(index + 1);

        byte[] rawSignature = base64Decoder.decode(base64Signature);

        return base64UrlEncoder.encodeToString(rawSignature);
    }
}