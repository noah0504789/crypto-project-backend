package org.example.oauth2.client.token.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.oauth2.client.token.application.port.out.AuthServerTokenPort;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccessTokenService {

    private final AuthServerTokenPort authServerTokenPort;

    public String findValue(String clientRegistrationId, String username) {
        return authServerTokenPort.findAccessToken(clientRegistrationId, username);
    }
}
