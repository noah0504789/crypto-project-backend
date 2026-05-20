package org.example.oauth2.token.dto;

public record SignRequest(
        String keyName,
        Integer keyVersion,
        String headerB64u,
        String payloadB64u) {
}
