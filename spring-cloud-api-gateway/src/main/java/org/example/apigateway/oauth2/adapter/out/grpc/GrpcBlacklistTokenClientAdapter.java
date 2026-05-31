package org.example.apigateway.oauth2.adapter.out.grpc;

import lombok.RequiredArgsConstructor;
import org.example.oauth2.authorizationserver.client.Oauth2AuthorizationServerClient;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GrpcBlacklistTokenClientAdapter {

    private final Oauth2AuthorizationServerClient authorizationServerClient;

    public boolean existsByAccessToken(String accessToken) {
        return authorizationServerClient.existsBlacklist(accessToken);
    }
}
