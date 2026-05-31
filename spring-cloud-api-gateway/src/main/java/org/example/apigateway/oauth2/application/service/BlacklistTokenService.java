package org.example.apigateway.oauth2.application.service;

import lombok.RequiredArgsConstructor;
import org.example.apigateway.oauth2.adapter.out.grpc.GrpcBlacklistTokenClientAdapter;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BlacklistTokenService {

    private final GrpcBlacklistTokenClientAdapter blacklistTokenClientAdapter;

    public boolean existsByAccessToken(String accessToken) {
        return blacklistTokenClientAdapter.existsByAccessToken(accessToken);
    }
}