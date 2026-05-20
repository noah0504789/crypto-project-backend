package org.example.common.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String keyName,
        Integer keyVersion,
        String issuerUri,
        String jwksUri,
        String signUri,
        Long accessTokenExpirationMs,
        Long refreshTokenExpirationMs,
        Integer connectTimeoutMs,
        Integer readTimeoutMs
) {
}
