package org.example.oauth2.port;

public interface BlacklistTokenPort {

    boolean existsByAccessToken(String accessToken);
}
