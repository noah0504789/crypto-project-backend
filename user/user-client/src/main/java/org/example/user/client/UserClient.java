package org.example.user.client;

import org.example.grpc.user.GrpcFindByEmailResponse;
import org.example.grpc.user.GrpcSignUpOauth2Response;

import java.util.concurrent.CompletableFuture;

public interface UserClient {

    CompletableFuture<GrpcFindByEmailResponse> findByEmail(String email);

    CompletableFuture<GrpcSignUpOauth2Response> signUpOauth2(
            String sub, String email, String nickname);
}
