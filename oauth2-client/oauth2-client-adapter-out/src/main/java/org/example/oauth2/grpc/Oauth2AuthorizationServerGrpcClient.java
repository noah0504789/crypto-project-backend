package org.example.oauth2.grpc;

import io.grpc.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.example.grpc.token.AccessTokenServiceGrpc;
import org.example.grpc.token.AuthorizedClientServiceGrpc;
import org.example.grpc.token.BlacklistTokenServiceGrpc;
import org.example.grpc.token.FindAccessTokenGrpcRequest;
import org.example.grpc.token.FindRefreshTokenGrpcRequest;
import org.example.grpc.token.RefreshTokenServiceGrpc;
import org.example.grpc.token.RegisterBlacklistTokenGrpcRequest;
import org.example.grpc.token.RemoveAuthorizedClientRequest;
import org.example.grpc.token.SaveAuthorizedClientRequest;
import org.example.oauth2.port.AuthServerTokenPort;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static java.util.stream.Collectors.toMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class Oauth2AuthorizationServerGrpcClient implements AuthServerTokenPort {

    @GrpcClient("oauth2-authorization-server-client")
    private Channel channel;

    public String findAccessToken(String clientRegistrationId, String username) {
        FindAccessTokenGrpcRequest request = FindAccessTokenGrpcRequest.newBuilder()
                .setClientRegistrationId(clientRegistrationId)
                .setUsername(username)
                .build();

        AccessTokenServiceGrpc.AccessTokenServiceBlockingStub stub = AccessTokenServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(3500, TimeUnit.MILLISECONDS);

        return stub.findValue(request).getValue();
    }

    public String findRefreshToken(String clientRegistrationId, String username) {
        FindRefreshTokenGrpcRequest request = FindRefreshTokenGrpcRequest.newBuilder()
                .setClientRegistrationId(clientRegistrationId)
                .setUsername(username)
                .build();

        RefreshTokenServiceGrpc.RefreshTokenServiceBlockingStub stub = RefreshTokenServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(3500, TimeUnit.MILLISECONDS);

        return stub.findValue(request).getValue();
    }

    public boolean registerBlacklist(String accessToken) {
        RegisterBlacklistTokenGrpcRequest request = RegisterBlacklistTokenGrpcRequest.newBuilder()
                .setAccessToken(accessToken)
                .build();

        BlacklistTokenServiceGrpc.BlacklistTokenServiceBlockingStub stub = BlacklistTokenServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(3500, TimeUnit.MILLISECONDS);

        return stub.register(request).getValue();
    }

    public boolean saveTokens(String clientRegistrationId, String email, Map<String, Object> claims, String accessToken, String refreshToken) {
        SaveAuthorizedClientRequest request = SaveAuthorizedClientRequest.newBuilder()
                .setClientRegistrationId(clientRegistrationId)
                .setEmail(email)
                .putAllClaims(claims.entrySet().stream().collect(toMap(Map.Entry::getKey, e -> String.valueOf(e.getValue()))))
                .setAccessToken(accessToken)
                .setRefreshToken(refreshToken)
                .build();

        return getAuthorizedClientServiceBlockingStub().save(request).getValue();
    }

    public boolean removeTokens(String email) {
        RemoveAuthorizedClientRequest request = RemoveAuthorizedClientRequest.newBuilder()
                .setEmail(email)
                .build();

        return getAuthorizedClientServiceBlockingStub().remove(request).getValue();
    }

    private AuthorizedClientServiceGrpc.AuthorizedClientServiceBlockingStub getAuthorizedClientServiceBlockingStub() {
        AuthorizedClientServiceGrpc.AuthorizedClientServiceBlockingStub stub = AuthorizedClientServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(3500, TimeUnit.MILLISECONDS);
        return stub;
    }
}
