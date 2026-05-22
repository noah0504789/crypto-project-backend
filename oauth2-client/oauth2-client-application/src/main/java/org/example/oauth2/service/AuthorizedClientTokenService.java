package org.example.oauth2.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.oauth2.port.out.AuthServerTokenClientPort;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthorizedClientTokenService {

    private final AuthServerTokenClientPort authServerTokenClientPort;

    public Boolean save(String clientRegistrationId, String email, Map<String, Object> claims, String accessToken, String refreshToken) {
        return authServerTokenClientPort.saveTokens(clientRegistrationId, email, claims, accessToken, refreshToken);
    }

    public Boolean removeAllByEmail(String email) {
        return authServerTokenClientPort.removeTokens(email);
    }
}
