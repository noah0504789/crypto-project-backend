package org.example.apigateway.oauth2.adapter.out.grpc;

import lombok.RequiredArgsConstructor;
import org.example.oauth2.authorizationserver.client.Oauth2AuthorizationServerClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class GrpcBlacklistTokenClientAdapter {

    private final Oauth2AuthorizationServerClient authorizationServerClient;

    public Mono<Boolean> existsByAccessToken(String accessToken) {
        return Mono.defer(() -> Mono.fromFuture(
                authorizationServerClient.existsBlacklist(accessToken)
        )).map(response -> response.getValue());
    }
}
