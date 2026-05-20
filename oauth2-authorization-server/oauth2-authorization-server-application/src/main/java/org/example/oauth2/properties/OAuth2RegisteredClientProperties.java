package org.example.oauth2.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "oauth2.registered-client")
public record OAuth2RegisteredClientProperties(
        String id,
        String registrationId,
        String secret
) {
}
