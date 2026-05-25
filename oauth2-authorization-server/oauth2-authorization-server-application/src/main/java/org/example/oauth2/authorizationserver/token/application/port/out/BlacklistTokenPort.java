package org.example.oauth2.authorizationserver.token.application.port.out;

public interface BlacklistTokenPort {

    void register(String accessToken);

    boolean existsByAccessToken(String accessToken);
}
