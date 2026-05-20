package org.example.oauth2.token.policy;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

public interface RefreshTokenPolicy {

    OAuth2RefreshToken resolve(
            HttpServletRequest request,
            OAuth2AccessTokenAuthenticationToken authentication,
            Authentication principal,
            RegisteredClient registeredClient
    );

    boolean shouldSave(
            HttpServletRequest request,
            OAuth2AccessTokenAuthenticationToken authentication
    );
}