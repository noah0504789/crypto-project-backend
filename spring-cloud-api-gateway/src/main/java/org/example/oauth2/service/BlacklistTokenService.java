package org.example.oauth2.service;

import lombok.RequiredArgsConstructor;
import org.example.oauth2.adapter.grpc.GrpcBlacklistTokenClientAdapter;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BlacklistTokenService {

    private final GrpcBlacklistTokenClientAdapter blacklistTokenClientAdapter;

    public boolean existsByAccessToken(String accessToken) {
        return blacklistTokenClientAdapter.existsByAccessToken(accessToken);
    }
}