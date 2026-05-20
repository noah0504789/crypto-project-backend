package org.example.oauth2;

import lombok.RequiredArgsConstructor;
import org.example.common.enums.RedisKey;
import org.example.oauth2.properties.OAuth2RegisteredClientProperties;
import org.example.oauth2.token.port.AccessTokenStore;
import org.example.oauth2.token.port.RefreshTokenStore;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.security.Principal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.security.oauth2.server.authorization.OAuth2TokenType.ACCESS_TOKEN;
import static org.springframework.security.oauth2.server.authorization.OAuth2TokenType.REFRESH_TOKEN;

@Service
@RequiredArgsConstructor
public class RedisOAuth2AuthorizationService implements OAuth2AuthorizationService {

    private final AccessTokenStore accessTokenStore;
    private final RefreshTokenStore refreshTokenStore;
    private final RegisteredClientRepository registeredClientRepository;
    private final OAuth2RegisteredClientProperties oAuth2RegisteredClientProperties;

    @Override
    public void save(OAuth2Authorization authorization) {
        if (authorization.getRefreshToken() == null) {
            return;
        }

        OAuth2RefreshToken refreshToken = authorization.getRefreshToken().getToken();
        String email = authorization.getPrincipalName();

        refreshTokenStore.cache(
                refreshToken.getTokenValue(),
                oAuth2RegisteredClientProperties.registrationId(),
                email
        );
    }

    @Override
    public void remove(OAuth2Authorization authorization) {
        // no-op
    }

    @Override
    public OAuth2Authorization findById(String id) {
        // no-use
        return null;
    }

    @Override
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
        Assert.hasText(token, "token cannot be empty");

        String accessClaimKey = RedisKey.ACCESS_CLAIMS.keyFor(token);
        String refreshEmailKey = RedisKey.REFRESH_TOKEN.keyFor(oAuth2RegisteredClientProperties.registrationId(), token);

        if (ACCESS_TOKEN.equals(tokenType)
                && accessTokenStore.existsByClaimKey(accessClaimKey)) {
            return getAccessOAuth2Authorization(token);
        }

        if (REFRESH_TOKEN.equals(tokenType)
                && refreshTokenStore.existsByEmailKey(refreshEmailKey)) {
            return getRefreshOAuth2Authorization(token);
        }

        return null;
    }

    private OAuth2Authorization getAccessOAuth2Authorization(String token) {
        Map<String, String> claims = accessTokenStore.findClaims(token);

        if (claims.isEmpty()) {
            return null;
        }

        RegisteredClient registeredClient = registeredClientRepository.findById(oAuth2RegisteredClientProperties.id());

        if (registeredClient == null) {
            return null;
        }
        
        String principalName = resolvePrincipalName(claims);
        Instant issuedAt = parseInstant(claims.get("iat"));
        Instant expiresAt = parseInstant(claims.get("exp"));

        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                token,
                issuedAt,
                expiresAt
        );

        return OAuth2Authorization
                .withRegisteredClient(registeredClient)
                .authorizationGrantType(AuthorizationGrantType.TOKEN_EXCHANGE)
                .principalName(principalName)
                .attribute(
                        Principal.class.getName(),
                        new UsernamePasswordAuthenticationToken(principalName, null)
                )
                .token(accessToken, metadata -> {
                    metadata.put(
                            OAuth2TokenFormat.class.getName(),
                            OAuth2TokenFormat.SELF_CONTAINED.getValue()
                    );

                    metadata.put(
                            OAuth2Authorization.Token.CLAIMS_METADATA_NAME,
                            new HashMap<>(claims)
                    );
                })
                .build();
    }

    private OAuth2Authorization getRefreshOAuth2Authorization(String token) {
        String email = refreshTokenStore.findEmail(oAuth2RegisteredClientProperties.registrationId(), token);

        if (email == null || email.isBlank()) {
            return null;
        }

        RegisteredClient registeredClient = registeredClientRepository.findById(oAuth2RegisteredClientProperties.id());

        if (registeredClient == null) {
            return null;
        }

        OAuth2RefreshToken refreshToken = new OAuth2RefreshToken(token, null);

        return OAuth2Authorization
                .withRegisteredClient(registeredClient)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .principalName(email)
                .attribute(
                        Principal.class.getName(),
                        new UsernamePasswordAuthenticationToken(email, null)
                )
                .token(refreshToken)
                .build();
    }

    private String resolvePrincipalName(Map<String, String> claims) {
        String email = claims.get("email");

        if (email != null && !email.isBlank()) {
            return email;
        }

        String sub = claims.get("sub");

        if (sub != null && !sub.isBlank()) {
            return sub;
        }

        throw new IllegalArgumentException("JWT claims must contain email or sub");
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.matches("\\d+")
                ? Instant.ofEpochSecond(Long.parseLong(value))
                : Instant.parse(value);
    }
}
