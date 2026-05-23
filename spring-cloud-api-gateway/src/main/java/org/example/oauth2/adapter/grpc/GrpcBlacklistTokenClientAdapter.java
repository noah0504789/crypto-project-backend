package org.example.oauth2.adapter.grpc;

import lombok.RequiredArgsConstructor;
import org.example.oauth2.client.Oauth2AuthorizationServerClient;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GrpcBlacklistTokenClientAdapter {

    private final Oauth2AuthorizationServerClient authorizationServerClient;

    public boolean existsByAccessToken(String accessToken) {
        return authorizationServerClient.existsBlacklist(accessToken);
    }
}
