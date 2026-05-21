package org.example.user.client;

import io.grpc.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.example.contract.user.UserResponse;
import org.example.grpc.user.FindByEmailGrpcRequest;
import org.example.grpc.user.FindByEmailGrpcResponse;
import org.example.grpc.user.SignUpOauth2GrpcRequest;
import org.example.grpc.user.SignUpOauth2GrpcResponse;
import org.example.grpc.user.UserGrpc;
import org.example.grpc.user.UserServiceGrpc;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class GrpcUserClient implements UserClient {

    @GrpcClient("user-client")
    private Channel channel;

    @Override
    public Optional<UserResponse> findByEmail(String email) {
        FindByEmailGrpcRequest request = FindByEmailGrpcRequest.newBuilder().setEmail(email).build();

        return Optional.ofNullable(stub().findByEmail(request))
                .filter(FindByEmailGrpcResponse::hasUser)
                .map(FindByEmailGrpcResponse::getUser)
                .map(this::toResponse);
    }

    @Override
    public UserResponse signUpOauth2(String sub, String email, String nickname) {
        SignUpOauth2GrpcRequest request = SignUpOauth2GrpcRequest.newBuilder()
                .setSub(sub)
                .setEmail(email)
                .setNickname(nickname)
                .build();

        SignUpOauth2GrpcResponse response = stub().signUpOauth2(request);

        return toResponse(response.getUser());
    }

    private UserServiceGrpc.UserServiceBlockingStub stub() {
        return UserServiceGrpc.newBlockingStub(channel).withDeadlineAfter(3500, TimeUnit.MILLISECONDS);
    }

    private UserResponse toResponse(UserGrpc user) {
        return UserResponse.builder()
                .id(user.getId())
                .sub(user.getSub())
                .nickname(user.getNickname())
                .email(user.getEmail())
                .roles(user.getRolesList())
                .createdAt(user.hasCreatedAt()
                        ? LocalDateTime.ofInstant(
                                Instant.ofEpochSecond(user.getCreatedAt().getSeconds(), user.getCreatedAt().getNanos()),
                                ZoneId.systemDefault()
                        )
                        : null)
                .build();
    }
}
