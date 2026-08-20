package org.example.oauth2.client.token.adapter.out.grpc;

import lombok.RequiredArgsConstructor;
import org.example.common.grpc.client.GrpcFutures;
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
        return GrpcFutures.join(
                authorizationServerClient.findAccessToken(clientRegistrationId, username))
                .getValue();
    }

    @Override
    public String findRefreshToken(String clientRegistrationId, String username) {
        return GrpcFutures.join(
                authorizationServerClient.findRefreshToken(clientRegistrationId, username))
                .getValue();
    }

    @Override
    public boolean registerBlacklist(String accessToken) {
        return GrpcFutures.join(authorizationServerClient.registerBlacklist(accessToken)).getValue();
    }

    @Override
    public boolean saveTokens(
            String clientRegistrationId,
            String email,
            Map<String, Object> claims,
            String accessToken,
            String refreshToken
    ) {
        return GrpcFutures.join(authorizationServerClient.saveTokens(
                        clientRegistrationId, email, claims, accessToken, refreshToken))
                .getValue();
    }

    @Override
    public boolean removeTokens(String email) {
        return GrpcFutures.join(authorizationServerClient.removeTokens(email)).getValue();
    }
}
