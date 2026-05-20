package org.example.sign;

public record SignResponse(
        String kid,
        String alg,
        String sigB64u) {
}
