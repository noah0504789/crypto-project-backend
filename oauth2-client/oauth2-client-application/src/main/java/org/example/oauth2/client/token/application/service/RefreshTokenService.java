package org.example.oauth2.client.token.application.service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.common.enums.AuthTokenKey;
import org.example.oauth2.client.properties.InternalAuthServerProperties;
import org.example.common.properties.JwtProperties;
import org.example.oauth2.client.exception.OAuth2ClientInfrastructureException;
import org.example.oauth2.client.token.application.port.out.AuthServerTokenClientPort;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2RefreshTokenGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Arrays;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final AuthServerTokenClientPort authServerTokenClientPort;
    private final JwtProperties jwtProperties;
    private final ClientRegistrationRepository clientRegistrationRepository;
    private final OAuth2AccessTokenResponseClient<OAuth2RefreshTokenGrantRequest> refreshTokenResponseClient;
    private final InternalAuthServerProperties internalAuthServerProperties;

    public String findValue(String clientRegistrationId, String username) {
        return authServerTokenClientPort.findRefreshToken(clientRegistrationId, username);
    }

    public OAuth2AccessTokenResponse reissue(String refreshTokenValue) {
        String clientRegistrationId = internalAuthServerProperties.clientRegistrationId();
        ClientRegistration clientRegistration = clientRegistrationRepository.findByRegistrationId(clientRegistrationId);

        if (clientRegistration == null) {
            throw new OAuth2ClientInfrastructureException(
                    "ClientRegistration not found. clientRegistrationId=" + clientRegistrationId
            );
        }

        OAuth2RefreshTokenGrantRequest grantRequest =
                new OAuth2RefreshTokenGrantRequest(
                        clientRegistration,
                        placeholderAccessTokenForRefreshGrant(),
                        new OAuth2RefreshToken(refreshTokenValue, null)
                );

        return refreshTokenResponseClient.getTokenResponse(grantRequest);
    }

    public String extractRefreshToken(HttpServletRequest request) {
        if (request == null || request.getCookies() == null) {
            return null;
        }

        return Arrays.stream(request.getCookies())
                .filter(cookie -> AuthTokenKey.REFRESH_TOKEN_COOKIE.value().equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    public ResponseCookie getResponseCookie(String refreshToken) {
        return ResponseCookie.from(AuthTokenKey.REFRESH_TOKEN_COOKIE.value(), refreshToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .maxAge(Duration.ofMillis(jwtProperties.refreshTokenExpirationMs()))
                .path("/")
                .build();
    }

    public ResponseCookie deleteResponseCookie(String refreshToken) {
        return ResponseCookie.from(AuthTokenKey.REFRESH_TOKEN_COOKIE.value(), refreshToken == null ? "" : refreshToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .maxAge(0)
                .path("/")
                .build();
    }

    private OAuth2AccessToken placeholderAccessTokenForRefreshGrant() {
        return new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "not-used",
                null,
                null
        );
    }
}
