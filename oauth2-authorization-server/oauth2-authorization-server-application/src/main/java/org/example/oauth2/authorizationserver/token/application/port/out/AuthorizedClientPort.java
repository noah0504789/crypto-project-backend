package org.example.oauth2.authorizationserver.token.application.port.out;

import java.util.Map;

public interface AuthorizedClientPort {

    boolean save(
            String clientRegistrationId,
            String email,
            String accessToken,
            String refreshToken,
            Map<String, String> claims
    );

    boolean remove(String email);
}
