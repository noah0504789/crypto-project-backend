package org.example.user.client;

import io.grpc.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.example.common.grpc.client.GrpcFutures;
import org.example.grpc.user.GrpcFindByEmailRequest;
import org.example.grpc.user.GrpcFindByEmailResponse;
import org.example.grpc.user.GrpcSignUpOauth2Request;
import org.example.grpc.user.GrpcSignUpOauth2Response;
import org.example.grpc.user.UserServiceGrpc;
import org.example.user.client.properties.GrpcUserClientProperties;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class GrpcUserClient implements UserClient {

    @GrpcClient("user-client")
    private Channel channel;

    private final GrpcUserClientProperties grpcUserClientProperties;

    @Override
    public CompletableFuture<GrpcFindByEmailResponse> findByEmail(String email) {
        GrpcFindByEmailRequest request = GrpcFindByEmailRequest.newBuilder().setEmail(email).build();

        return GrpcFutures.toCompletableFuture(stub().findByEmail(request));
    }

    @Override
    public CompletableFuture<GrpcSignUpOauth2Response> signUpOauth2(
            String sub, String email, String nickname) {
        GrpcSignUpOauth2Request request = GrpcSignUpOauth2Request.newBuilder()
                .setSub(sub)
                .setEmail(email)
                .setNickname(nickname)
                .build();

        return GrpcFutures.toCompletableFuture(stub().signUpOauth2(request));
    }

    private UserServiceGrpc.UserServiceFutureStub stub() {
        return UserServiceGrpc.newFutureStub(channel)
                .withDeadlineAfter(grpcUserClientProperties.deadlineMillis(), TimeUnit.MILLISECONDS);
    }
}
