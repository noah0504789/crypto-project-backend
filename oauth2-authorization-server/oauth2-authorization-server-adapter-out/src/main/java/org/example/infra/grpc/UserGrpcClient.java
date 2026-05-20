package org.example.infra.grpc;

import io.grpc.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.example.grpc.user.FindByEmailGrpcRequest;
import org.example.grpc.user.FindByEmailGrpcResponse;
import org.example.grpc.user.UserGrpc;
import org.example.grpc.user.UserServiceGrpc;
import org.example.user.UserLookupPort;
import org.example.user.UserResponse;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserGrpcClient implements UserLookupPort {

    @GrpcClient("user-client")
    private Channel channel;

    public Optional<UserResponse> findByEmail(String email) {
        FindByEmailGrpcRequest request = FindByEmailGrpcRequest.newBuilder().setEmail(email).build();

        return Optional.ofNullable(stub().findByEmail(request))
                .filter(FindByEmailGrpcResponse::hasUser)
                .map(FindByEmailGrpcResponse::getUser)
                .map(this::toResponse);
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
                .createdAt(LocalDateTime.ofInstant(
                        Instant.ofEpochSecond(user.getCreatedAt().getSeconds(), user.getCreatedAt().getNanos()),
                        ZoneId.systemDefault()
                ))
                .build();
    }
}
