package org.example.oauth2.authorizationserver.client;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.BoolValue;
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
import org.example.oauth2.authorizationserver.client.properties.GrpcOauth2AuthorizationServerClientProperties;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static java.util.stream.Collectors.toMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class GrpcOauth2AuthorizationServerClient implements Oauth2AuthorizationServerClient {

    @GrpcClient("oauth2-authorization-server-client")
    private Channel channel;

    private final GrpcOauth2AuthorizationServerClientProperties grpcOauth2AuthorizationServerClientProperties;
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
    public CompletableFuture<Boolean> existsBlacklistAsync(String accessToken) {
        GrpcExistsBlacklistTokenRequest request = GrpcExistsBlacklistTokenRequest.newBuilder()
                .setAccessToken(accessToken)
                .build();

        return toCompletableFuture(blacklistTokenFutureStub().exists(request), BoolValue::getValue);
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
        return AccessTokenServiceGrpc.newBlockingStub(channel).withDeadlineAfter(grpcOauth2AuthorizationServerClientProperties.deadlineMillis(), TimeUnit.MILLISECONDS);
    }

    private RefreshTokenServiceGrpc.RefreshTokenServiceBlockingStub refreshTokenStub() {
        return RefreshTokenServiceGrpc.newBlockingStub(channel).withDeadlineAfter(grpcOauth2AuthorizationServerClientProperties.deadlineMillis(), TimeUnit.MILLISECONDS);
    }

    private BlacklistTokenServiceGrpc.BlacklistTokenServiceBlockingStub blacklistTokenStub() {
        return BlacklistTokenServiceGrpc.newBlockingStub(channel).withDeadlineAfter(grpcOauth2AuthorizationServerClientProperties.deadlineMillis(), TimeUnit.MILLISECONDS);
    }

    private BlacklistTokenServiceGrpc.BlacklistTokenServiceFutureStub blacklistTokenFutureStub() {
        return BlacklistTokenServiceGrpc.newFutureStub(channel).withDeadlineAfter(grpcOauth2AuthorizationServerClientProperties.deadlineMillis(), TimeUnit.MILLISECONDS);
    }

    private AuthorizedClientServiceGrpc.AuthorizedClientServiceBlockingStub authorizedClientStub() {
        return AuthorizedClientServiceGrpc.newBlockingStub(channel).withDeadlineAfter(grpcOauth2AuthorizationServerClientProperties.deadlineMillis(), TimeUnit.MILLISECONDS);
    }

    private <T, R> CompletableFuture<R> toCompletableFuture(ListenableFuture<T> grpcFuture, Function<T, R> mapper) {
        CompletableFuture<R> resultFuture = new CompletableFuture<>();

        Futures.addCallback(grpcFuture, new FutureCallback<>() {
            @Override
            public void onSuccess(T result) {
                try {
                    resultFuture.complete(mapper.apply(Objects.requireNonNull(result, "gRPC returned null")));
                } catch (Throwable error) {
                    resultFuture.completeExceptionally(error);
                }
            }

            @Override
            public void onFailure(Throwable error) {
                resultFuture.completeExceptionally(error);
            }
        }, MoreExecutors.directExecutor());

        resultFuture.whenComplete((result, error) -> {
            if (resultFuture.isCancelled()) grpcFuture.cancel(true);
        });

        return resultFuture;
    }
}
