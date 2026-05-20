package org.example.oauth2.token.policy;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.token.DefaultOAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RotatingRefreshTokenPolicy implements RefreshTokenPolicy {

    private final OAuth2TokenGenerator<?> tokenGenerator;

    @Override
    public OAuth2RefreshToken resolve(
            HttpServletRequest request,
            OAuth2AccessTokenAuthenticationToken authentication,
            Authentication principal,
            RegisteredClient registeredClient
    ) {
        return generateRefreshToken(
                request,
                authentication,
                principal,
                registeredClient
        );
    }

    @Override
    public boolean shouldSave(
            HttpServletRequest request,
            OAuth2AccessTokenAuthenticationToken authentication
    ) {
        return true;
    }

    private OAuth2RefreshToken generateRefreshToken(
            HttpServletRequest request,
            OAuth2AccessTokenAuthenticationToken authentication,
            Authentication principal,
            RegisteredClient registeredClient
    ) {
        OAuth2TokenContext context = DefaultOAuth2TokenContext.builder()
                .principal(principal)
                .registeredClient(registeredClient)
                .authorizedScopes(authentication.getAccessToken().getScopes())
                .authorizationGrantType(resolveAuthorizationGrantType(request))
                .authorizationGrant(authentication)
                .tokenType(OAuth2TokenType.REFRESH_TOKEN)
                .build();

        OAuth2Token generatedToken = tokenGenerator.generate(context);

        if (!(generatedToken instanceof OAuth2RefreshToken refreshToken)) {
            throw new IllegalStateException("Failed to generate refresh token");
        }

        return refreshToken;
    }

    private AuthorizationGrantType resolveAuthorizationGrantType(HttpServletRequest request) {
        String grantType = request.getParameter(OAuth2ParameterNames.GRANT_TYPE);

        if (AuthorizationGrantType.REFRESH_TOKEN.getValue().equals(grantType)) {
            return AuthorizationGrantType.REFRESH_TOKEN;
        }

        return AuthorizationGrantType.TOKEN_EXCHANGE;
    }
}