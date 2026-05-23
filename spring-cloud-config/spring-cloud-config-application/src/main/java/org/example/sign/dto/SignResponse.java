package org.example.sign.dto;

public record SignResponse(
        String kid,
        String alg,
        String sigB64u) {
}
