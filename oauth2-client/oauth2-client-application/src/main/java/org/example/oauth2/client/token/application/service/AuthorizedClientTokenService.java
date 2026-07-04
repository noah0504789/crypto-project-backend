package org.example.oauth2.client.token.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.oauth2.client.token.application.port.out.AuthServerTokenPort;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthorizedClientTokenService {

    private final AuthServerTokenPort authServerTokenPort;

    public Boolean save(String clientRegistrationId, String email, Map<String, Object> claims, String accessToken, String refreshToken) {
        return authServerTokenPort.saveTokens(clientRegistrationId, email, claims, accessToken, refreshToken);
    }

    public Boolean removeAllByEmail(String email) {
        return authServerTokenPort.removeTokens(email);
    }
}
