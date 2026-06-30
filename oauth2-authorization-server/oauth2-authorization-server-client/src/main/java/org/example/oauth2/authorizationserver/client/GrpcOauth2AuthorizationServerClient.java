package org.example.oauth2.authorizationserver.client;

import io.grpc.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.example.grpc.auth.AccessTokenServiceGrpc;
import org.example.grpc.auth.AuthorizedClientServiceGrpc;
import org.example.grpc.auth.BlacklistTokenServiceGrpc;
import org.example.grpc.auth.GrpcExistsBlacklistTokenRequest;
import org.example.grpc.auth.GrpcFindAccessTokenRequest;
import org.example.grpc.auth.GrpcFindRefreshTokenRequest;
import org.example.grpc.auth.RefreshTokenServiceGrpc;
import org.example.grpc.auth.GrpcRegisterBlacklistTokenRequest;
import org.example.grpc.auth.RemoveAuthorizedClientRequest;
import org.example.grpc.auth.SaveAuthorizedClientRequest;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static java.util.stream.Collectors.toMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class GrpcOauth2AuthorizationServerClient implements Oauth2AuthorizationServerClient {

    @GrpcClient("oauth2-authorization-server-client")
    private Channel channel;

    @Override
    public String findAccessToken(String clientRegistrationId, String username) {
        GrpcFindAccessTokenRequest request = GrpcFindAccessTokenRequest.newBuilder()
                .setClientRegistrationId(clientRegistrationId)
                .setUsername(username)
                .build();

        return accessTokenStub().findValue(request).getValue();
    }

    @Override
    public String findRefreshToken(String clientRegistrationId, String username) {
        GrpcFindRefreshTokenRequest request = GrpcFindRefreshTokenRequest.newBuilder()
                .setClientRegistrationId(clientRegistrationId)
                .setUsername(username)
                .build();

        return refreshTokenStub().findValue(request).getValue();
    }

    @Override
    public boolean registerBlacklist(String accessToken) {
        GrpcRegisterBlacklistTokenRequest request = GrpcRegisterBlacklistTokenRequest.newBuilder()
                .setAccessToken(accessToken)
                .build();

        return blacklistTokenStub().register(request).getValue();
    }

    @Override
    public boolean existsBlacklist(String accessToken) {
        GrpcExistsBlacklistTokenRequest request = GrpcExistsBlacklistTokenRequest.newBuilder()
                .setAccessToken(accessToken)
                .build();

        return blacklistTokenStub().exists(request).getValue();
    }

    @Override
    public boolean saveTokens(
            String clientRegistrationId,
            String email,
            Map<String, Object> claims,
            String accessToken,
            String refreshToken
    ) {
        SaveAuthorizedClientRequest request = SaveAuthorizedClientRequest.newBuilder()
                .setClientRegistrationId(clientRegistrationId)
                .setEmail(email)
                .putAllClaims(claims.entrySet().stream().collect(toMap(Map.Entry::getKey, e -> String.valueOf(e.getValue()))))
                .setAccessToken(accessToken)
                .setRefreshToken(refreshToken)
                .build();

        return authorizedClientStub().save(request).getValue();
    }

    @Override
    public boolean removeTokens(String email) {
        RemoveAuthorizedClientRequest request = RemoveAuthorizedClientRequest.newBuilder()
                .setEmail(email)
                .build();

        return authorizedClientStub().remove(request).getValue();
    }

    private AccessTokenServiceGrpc.AccessTokenServiceBlockingStub accessTokenStub() {
        return AccessTokenServiceGrpc.newBlockingStub(channel).withDeadlineAfter(3500, TimeUnit.MILLISECONDS);
    }

    private RefreshTokenServiceGrpc.RefreshTokenServiceBlockingStub refreshTokenStub() {
        return RefreshTokenServiceGrpc.newBlockingStub(channel).withDeadlineAfter(3500, TimeUnit.MILLISECONDS);
    }

    private BlacklistTokenServiceGrpc.BlacklistTokenServiceBlockingStub blacklistTokenStub() {
        return BlacklistTokenServiceGrpc.newBlockingStub(channel).withDeadlineAfter(3500, TimeUnit.MILLISECONDS);
    }

    private AuthorizedClientServiceGrpc.AuthorizedClientServiceBlockingStub authorizedClientStub() {
        return AuthorizedClientServiceGrpc.newBlockingStub(channel).withDeadlineAfter(3500, TimeUnit.MILLISECONDS);
    }
}
