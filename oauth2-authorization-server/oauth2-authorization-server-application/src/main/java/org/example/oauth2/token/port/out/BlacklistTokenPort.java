package org.example.oauth2.token.port.out;

public interface BlacklistTokenPort {

    void register(String accessToken);

    boolean existsByAccessToken(String accessToken);
}
