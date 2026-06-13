package org.example.common.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "frontend")
public record FrontendProperties(
        String origin,
        OAuth2 oauth2
) {

    public record OAuth2(
            String successRedirectPath,
            String failureRedirectPath
    ) {
    }

    public String successRedirectUri() {
        return origin + oauth2.successRedirectPath();
    }

    public String failureRedirectUri() {
        return origin + oauth2.failureRedirectPath();
    }
}