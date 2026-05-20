package org.example.oauth2;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.example.oauth2.token.policy.RefreshTokenPolicy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.Principal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final Base64.Decoder base64UrlDecoder;
    private final HttpMessageConverter<OAuth2AccessTokenResponse> accessTokenResponseConverter;
    private final OAuth2AuthorizationService authorizationService;
    private final RefreshTokenPolicy rotatingRefreshTokenPolicy;
    private final ObjectMapper objectMapper;

    public CustomAuthenticationSuccessHandler(
            HttpMessageConverter<OAuth2AccessTokenResponse> accessTokenResponseConverter,
            OAuth2AuthorizationService authorizationService,
            @Qualifier("rotatingRefreshTokenPolicy") RefreshTokenPolicy rotatingRefreshTokenPolicy,
            ObjectMapper objectMapper) {
        this.base64UrlDecoder = Base64.getUrlDecoder();
        this.accessTokenResponseConverter = accessTokenResponseConverter;
        this.authorizationService = authorizationService;
        this.rotatingRefreshTokenPolicy = rotatingRefreshTokenPolicy;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException {
        OAuth2AccessTokenAuthenticationToken tokenAuthentication = castToAccessTokenAuthentication(authentication);
        Authentication principal = castToAuthenticationPrincipal(tokenAuthentication.getPrincipal());
        RegisteredClient registeredClient = tokenAuthentication.getRegisteredClient();
        OAuth2AccessToken accessToken = tokenAuthentication.getAccessToken();
        OAuth2RefreshToken refreshToken =
                rotatingRefreshTokenPolicy.resolve(
                        request,
                        tokenAuthentication,
                        principal,
                        registeredClient
                );

        Map<String, String> claims = extractStringClaims(accessToken.getTokenValue());

        saveAuthorizationIfRequired(
                request,
                tokenAuthentication,
                registeredClient,
                refreshToken,
                claims
        );

        writeTokenResponse(response, accessToken, refreshToken);
    }

    private OAuth2AccessTokenAuthenticationToken castToAccessTokenAuthentication(Authentication authentication) {
        if (!(authentication instanceof OAuth2AccessTokenAuthenticationToken tokenAuthentication)) {
            throw new IllegalArgumentException(
                    "Unsupported authentication type: " + authentication.getClass().getName()
            );
        }

        return tokenAuthentication;
    }

    private Authentication castToAuthenticationPrincipal(Object principal) {
        if (!(principal instanceof Authentication authenticationPrincipal)) {
            throw new IllegalArgumentException(
                    "Unsupported principal type: " + principal.getClass().getName()
            );
        }

        return authenticationPrincipal;
    }

    private void saveAuthorizationIfRequired(
            HttpServletRequest request,
            OAuth2AccessTokenAuthenticationToken tokenAuthentication,
            RegisteredClient registeredClient,
            OAuth2RefreshToken refreshToken,
            Map<String, String> claims
    ) {
        if (!rotatingRefreshTokenPolicy.shouldSave(request, tokenAuthentication)) {
            return;
        }

        OAuth2Authorization authorization =
                buildRefreshTokenAuthorization(
                        registeredClient,
                        refreshToken,
                        claims
                );

        authorizationService.save(authorization);
    }

    private OAuth2Authorization buildRefreshTokenAuthorization(
            RegisteredClient registeredClient,
            OAuth2RefreshToken refreshToken,
            Map<String, String> claims
    ) {
        String principalName = resolvePrincipalName(claims);

        return OAuth2Authorization
                .withRegisteredClient(registeredClient)
                .authorizationGrantType(AuthorizationGrantType.TOKEN_EXCHANGE)
                .principalName(principalName)
                .attribute(
                        Principal.class.getName(),
                        new UsernamePasswordAuthenticationToken(principalName, null)
                )
                .refreshToken(refreshToken)
                .build();
    }

    private String resolvePrincipalName(Map<String, String> claims) {
        String sub = claims.get("sub");

        if (sub != null && !sub.isBlank()) {
            return sub;
        }

        throw new IllegalArgumentException("JWT claim 'sub' is missing");
    }

    private void writeTokenResponse(
            HttpServletResponse response,
            OAuth2AccessToken accessToken,
            OAuth2RefreshToken refreshToken
    ) throws IOException {
        OAuth2AccessTokenResponse accessTokenResponse =
                OAuth2AccessTokenResponse.withToken(accessToken.getTokenValue())
                        .tokenType(accessToken.getTokenType())
                        .scopes(accessToken.getScopes())
                        .expiresIn(resolveExpiresIn(accessToken))
                        .refreshToken(refreshToken.getTokenValue())
                        .build();

        ServletServerHttpResponse httpResponse = new ServletServerHttpResponse(response);

        accessTokenResponseConverter.write(
                accessTokenResponse,
                null,
                httpResponse
        );
    }

    private long resolveExpiresIn(OAuth2AccessToken accessToken) {
        Instant issuedAt = accessToken.getIssuedAt();
        Instant expiresAt = accessToken.getExpiresAt();

        if (issuedAt == null || expiresAt == null) {
            return 0L;
        }

        return ChronoUnit.SECONDS.between(issuedAt, expiresAt);
    }

    private Map<String, String> extractStringClaims(String jwt) throws IOException {
        String[] parts = jwt.split("\\.");

        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid JWT format");
        }

        byte[] decodedPayload = base64UrlDecoder.decode(parts[1]);
        Map<String, Object> rawClaims = objectMapper.readValue(decodedPayload, new TypeReference<>() {});

        return rawClaims.entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> String.valueOf(entry.getValue())
                ));
    }
}