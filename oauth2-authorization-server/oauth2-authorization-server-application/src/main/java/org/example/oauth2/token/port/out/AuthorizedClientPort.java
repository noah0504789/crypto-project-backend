package org.example.oauth2.token.port.out;

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
