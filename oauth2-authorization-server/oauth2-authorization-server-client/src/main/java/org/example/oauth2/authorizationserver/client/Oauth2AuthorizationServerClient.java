package org.example.oauth2.authorizationserver.client;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface Oauth2AuthorizationServerClient {

    String findAccessToken(String clientRegistrationId, String username);

    String findRefreshToken(String clientRegistrationId, String username);

    boolean registerBlacklist(String accessToken);

    boolean existsBlacklist(String accessToken);

    CompletableFuture<Boolean> existsBlacklistAsync(String accessToken);

    boolean saveTokens(
            String clientRegistrationId,
            String email,
            Map<String, Object> claims,
            String accessToken,
            String refreshToken
    );

    boolean removeTokens(String email);
}
