package org.example.oauth2.token.port;

import java.time.Duration;

public interface RefreshTokenStore {

    void cache(String refreshToken, String clientRegistrationId, String email);

    Duration getTTL();

    String findValue(String clientRegistrationId, String username);

    String findEmail(String clientRegistrationId, String token);

    boolean existsByEmailKey(String emailKey);
}
