package org.example.configserver.sign.dto;

public record SignResponse(
        String kid,
        String alg,
        String sigB64u) {
}
