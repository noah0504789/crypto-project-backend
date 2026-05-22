package org.example.oauth2.service.token;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.oauth2.port.out.AuthServerTokenClientPort;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BlacklistTokenService {

    private final AuthServerTokenClientPort authServerTokenClientPort;

    public Boolean register(String accessToken) {
        return authServerTokenClientPort.registerBlacklist(accessToken);
    }
}
