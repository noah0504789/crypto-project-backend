package org.example.oauth2.client;

import java.util.Map;

public interface Oauth2AuthorizationServerClient {

    String findAccessToken(String clientRegistrationId, String username);

    String findRefreshToken(String clientRegistrationId, String username);

    boolean registerBlacklist(String accessToken);

    boolean existsBlacklist(String accessToken);

    boolean saveTokens(
            String clientRegistrationId,
            String email,
            Map<String, Object> claims,
            String accessToken,
            String refreshToken
    );

    boolean removeTokens(String email);
}
