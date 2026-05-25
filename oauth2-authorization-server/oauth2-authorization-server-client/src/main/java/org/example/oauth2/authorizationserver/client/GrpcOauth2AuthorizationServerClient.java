package org.example.oauth2.authorizationserver.client;

import io.grpc.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.example.grpc.token.AccessTokenServiceGrpc;
import org.example.grpc.token.AuthorizedClientServiceGrpc;
import org.example.grpc.token.BlacklistTokenServiceGrpc;
import org.example.grpc.token.ExistsBlacklistTokenGrpcRequest;
import org.example.grpc.token.FindAccessTokenGrpcRequest;
import org.example.grpc.token.FindRefreshTokenGrpcRequest;
import org.example.grpc.token.RefreshTokenServiceGrpc;
import org.example.grpc.token.RegisterBlacklistTokenGrpcRequest;
import org.example.grpc.token.RemoveAuthorizedClientRequest;
import org.example.grpc.token.SaveAuthorizedClientRequest;
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
        FindAccessTokenGrpcRequest request = FindAccessTokenGrpcRequest.newBuilder()
                .setClientRegistrationId(clientRegistrationId)
                .setUsername(username)
                .build();

        return accessTokenStub().findValue(request).getValue();
    }

    @Override
    public String findRefreshToken(String clientRegistrationId, String username) {
        FindRefreshTokenGrpcRequest request = FindRefreshTokenGrpcRequest.newBuilder()
                .setClientRegistrationId(clientRegistrationId)
                .setUsername(username)
                .build();

        return refreshTokenStub().findValue(request).getValue();
    }

    @Override
    public boolean registerBlacklist(String accessToken) {
        RegisterBlacklistTokenGrpcRequest request = RegisterBlacklistTokenGrpcRequest.newBuilder()
                .setAccessToken(accessToken)
                .build();

        return blacklistTokenStub().register(request).getValue();
    }

    @Override
    public boolean existsBlacklist(String accessToken) {
        ExistsBlacklistTokenGrpcRequest request = ExistsBlacklistTokenGrpcRequest.newBuilder()
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
