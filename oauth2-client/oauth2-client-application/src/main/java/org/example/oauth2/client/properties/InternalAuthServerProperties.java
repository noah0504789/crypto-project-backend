package org.example.oauth2.client.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "oauth2.internal-auth-server")
public record InternalAuthServerProperties(
        String clientRegistrationId
) {
}