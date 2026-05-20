package org.example.oauth2.token.port;

public interface BlacklistTokenStore {

    void register(String accessToken);

    boolean existsByAccessToken(String accessToken);
}
