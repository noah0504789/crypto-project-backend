package org.example.oauth2.grpc;

import lombok.RequiredArgsConstructor;
import org.example.oauth2.client.Oauth2AuthorizationServerClient;
import org.example.oauth2.port.AuthServerTokenPort;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class Oauth2AuthorizationServerClientAdapter implements AuthServerTokenPort {

    private final Oauth2AuthorizationServerClient authorizationServerClient;

    @Override
    public String findAccessToken(String clientRegistrationId, String username) {
        return authorizationServerClient.findAccessToken(clientRegistrationId, username);
    }

    @Override
    public String findRefreshToken(String clientRegistrationId, String username) {
        return authorizationServerClient.findRefreshToken(clientRegistrationId, username);
    }

    @Override
    public boolean registerBlacklist(String accessToken) {
        return authorizationServerClient.registerBlacklist(accessToken);
    }

    @Override
    public boolean saveTokens(
            String clientRegistrationId,
            String email,
            Map<String, Object> claims,
            String accessToken,
            String refreshToken
    ) {
        return authorizationServerClient.saveTokens(clientRegistrationId, email, claims, accessToken, refreshToken);
    }

    @Override
    public boolean removeTokens(String email) {
        return authorizationServerClient.removeTokens(email);
    }
}
