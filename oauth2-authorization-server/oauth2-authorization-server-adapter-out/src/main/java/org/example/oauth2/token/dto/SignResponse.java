package org.example.oauth2.token.dto;

public record SignResponse(
        String kid,
        String alg,
        String sigB64u) {
}
