package org.example.oauth2.authorizationserver.token.adapter.out.vault.dto;

public record SignRequest(
        String keyName,
        Integer keyVersion,
        String headerB64u,
        String payloadB64u) {
}
