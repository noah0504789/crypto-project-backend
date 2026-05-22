package org.example.oauth2.token.adapter.out.vault.dto;

public record SignResponse(
        String kid,
        String alg,
        String sigB64u) {
}
