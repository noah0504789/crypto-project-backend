package org.example.oauth2.authorizationserver.authorization.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "oauth2.registered-client")
public record OAuth2RegisteredClientProperties(
        String id,
        String registrationId,
        String secret
) {
}
