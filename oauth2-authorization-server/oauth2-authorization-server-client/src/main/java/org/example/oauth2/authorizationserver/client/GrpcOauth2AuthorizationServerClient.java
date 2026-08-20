package org.example.oauth2.authorizationserver.client;

import com.google.protobuf.BoolValue;
import com.google.protobuf.StringValue;
import io.grpc.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.example.common.grpc.client.GrpcFutures;
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
import org.example.oauth2.authorizationserver.client.properties.GrpcOauth2AuthorizationServerClientProperties;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static java.util.stream.Collectors.toMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class GrpcOauth2AuthorizationServerClient implements Oauth2AuthorizationServerClient {

    @GrpcClient("oauth2-authorization-server-client")
    private Channel channel;

    private final GrpcOauth2AuthorizationServerClientProperties grpcOauth2AuthorizationServerClientProperties;

    @Override
    public CompletableFuture<StringValue> findAccessToken(String clientRegistrationId, String username) {
        GrpcFindAccessTokenRequest request = GrpcFindAccessTokenRequest.newBuilder()
                .setClientRegistrationId(clientRegistrationId)
                .setUsername(username)
                .build();

        return GrpcFutures.toCompletableFuture(accessTokenStub().findValue(request));
    }

    @Override
    public CompletableFuture<StringValue> findRefreshToken(String clientRegistrationId, String username) {
        GrpcFindRefreshTokenRequest request = GrpcFindRefreshTokenRequest.newBuilder()
                .setClientRegistrationId(clientRegistrationId)
                .setUsername(username)
                .build();

        return GrpcFutures.toCompletableFuture(refreshTokenStub().findValue(request));
    }

    @Override
    public CompletableFuture<BoolValue> registerBlacklist(String accessToken) {
        GrpcRegisterBlacklistTokenRequest request = GrpcRegisterBlacklistTokenRequest.newBuilder()
                .setAccessToken(accessToken)
                .build();

        return GrpcFutures.toCompletableFuture(blacklistTokenStub().register(request));
    }

    @Override
    public CompletableFuture<BoolValue> existsBlacklist(String accessToken) {
        GrpcExistsBlacklistTokenRequest request = GrpcExistsBlacklistTokenRequest.newBuilder()
                .setAccessToken(accessToken)
                .build();

        return GrpcFutures.toCompletableFuture(blacklistTokenStub().exists(request));
    }

    @Override
    public CompletableFuture<BoolValue> saveTokens(
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

        return GrpcFutures.toCompletableFuture(authorizedClientStub().save(request));
    }

    @Override
    public CompletableFuture<BoolValue> removeTokens(String email) {
        RemoveAuthorizedClientRequest request = RemoveAuthorizedClientRequest.newBuilder()
                .setEmail(email)
                .build();

        return GrpcFutures.toCompletableFuture(authorizedClientStub().remove(request));
    }

    private AccessTokenServiceGrpc.AccessTokenServiceFutureStub accessTokenStub() {
        return AccessTokenServiceGrpc.newFutureStub(channel)
                .withDeadlineAfter(grpcOauth2AuthorizationServerClientProperties.deadlineMillis(), TimeUnit.MILLISECONDS);
    }

    private RefreshTokenServiceGrpc.RefreshTokenServiceFutureStub refreshTokenStub() {
        return RefreshTokenServiceGrpc.newFutureStub(channel)
                .withDeadlineAfter(grpcOauth2AuthorizationServerClientProperties.deadlineMillis(), TimeUnit.MILLISECONDS);
    }

    private BlacklistTokenServiceGrpc.BlacklistTokenServiceFutureStub blacklistTokenStub() {
        return BlacklistTokenServiceGrpc.newFutureStub(channel)
                .withDeadlineAfter(grpcOauth2AuthorizationServerClientProperties.deadlineMillis(), TimeUnit.MILLISECONDS);
    }

    private AuthorizedClientServiceGrpc.AuthorizedClientServiceFutureStub authorizedClientStub() {
        return AuthorizedClientServiceGrpc.newFutureStub(channel)
                .withDeadlineAfter(grpcOauth2AuthorizationServerClientProperties.deadlineMillis(), TimeUnit.MILLISECONDS);
    }
}
