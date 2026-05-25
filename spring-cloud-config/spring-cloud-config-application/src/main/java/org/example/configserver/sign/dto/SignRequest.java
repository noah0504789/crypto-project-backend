package org.example.configserver.sign.dto;

public record SignRequest(
        String keyName,
        Integer keyVersion,
        String headerB64u,
        String payloadB64u) {
}
