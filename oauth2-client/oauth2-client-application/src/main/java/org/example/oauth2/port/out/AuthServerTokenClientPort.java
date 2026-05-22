package org.example.oauth2.port;

import java.util.Map;

public interface AuthServerTokenPort {

    String findAccessToken(String clientRegistrationId, String username);

    String findRefreshToken(String clientRegistrationId, String username);

    boolean registerBlacklist(String accessToken);

    boolean saveTokens(
            String clientRegistrationId,
            String email,
            Map<String, Object> claims,
            String accessToken,
            String refreshToken
    );

    boolean removeTokens(String email);
}
