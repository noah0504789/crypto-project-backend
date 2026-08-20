package org.example.oauth2.authorizationserver.client;

import com.google.protobuf.BoolValue;
import com.google.protobuf.StringValue;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface Oauth2AuthorizationServerClient {

    CompletableFuture<StringValue> findAccessToken(String clientRegistrationId, String username);

    CompletableFuture<StringValue> findRefreshToken(String clientRegistrationId, String username);

    CompletableFuture<BoolValue> registerBlacklist(String accessToken);

    CompletableFuture<BoolValue> existsBlacklist(String accessToken);

    CompletableFuture<BoolValue> saveTokens(
            String clientRegistrationId,
            String email,
            Map<String, Object> claims,
            String accessToken,
            String refreshToken
    );

    CompletableFuture<BoolValue> removeTokens(String email);
}
