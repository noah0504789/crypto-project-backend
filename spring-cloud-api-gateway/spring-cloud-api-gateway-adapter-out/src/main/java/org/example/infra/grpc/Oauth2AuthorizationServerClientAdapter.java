package org.example.infra.grpc;

import lombok.RequiredArgsConstructor;
import org.example.oauth2.client.Oauth2AuthorizationServerClient;
import org.example.oauth2.port.BlacklistTokenPort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class Oauth2AuthorizationServerClientAdapter implements BlacklistTokenPort {

    private final Oauth2AuthorizationServerClient authorizationServerClient;

    @Override
    public boolean existsByAccessToken(String accessToken) {
        return authorizationServerClient.existsBlacklist(accessToken);
    }
}
