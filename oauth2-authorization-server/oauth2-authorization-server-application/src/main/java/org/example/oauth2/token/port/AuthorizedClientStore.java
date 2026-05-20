package org.example.oauth2.token.port;

import java.util.Map;

public interface AuthorizedClientStore {

    boolean save(
            String clientRegistrationId,
            String email,
            String accessToken,
            String refreshToken,
            Map<String, String> claims
    );

    boolean remove(String email);
}
