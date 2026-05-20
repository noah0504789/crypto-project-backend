package org.example.oauth2.service;

import lombok.RequiredArgsConstructor;
import org.example.oauth2.port.BlacklistTokenPort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BlacklistTokenService {

    private final BlacklistTokenPort blacklistTokenPort;

    public boolean existsByAccessToken(String accessToken) {
        return blacklistTokenPort.existsByAccessToken(accessToken);
    }
}
