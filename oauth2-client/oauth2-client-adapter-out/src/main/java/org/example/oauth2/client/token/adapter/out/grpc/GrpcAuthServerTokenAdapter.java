package org.example.oauth2.client.token.adapter.out.grpc;

import lombok.RequiredArgsConstructor;
import org.example.oauth2.authorizationserver.client.Oauth2AuthorizationServerClient;
import org.example.oauth2.client.token.application.port.out.AuthServerTokenPort;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class GrpcAuthServerTokenAdapter implements AuthServerTokenPort {

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
