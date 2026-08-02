package org.example.user.client;

import io.grpc.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.example.common.time.ServiceTimeConverter;
import org.example.contract.user.UserResponse;
import org.example.grpc.user.GrpcFindByEmailRequest;
import org.example.grpc.user.GrpcFindByEmailResponse;
import org.example.grpc.user.GrpcSignUpOauth2Request;
import org.example.grpc.user.GrpcSignUpOauth2Response;
import org.example.grpc.user.GrpcUser;
import org.example.grpc.user.UserServiceGrpc;
import org.springframework.stereotype.Service;

import java.time.Instant;
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
        GrpcFindByEmailRequest request = GrpcFindByEmailRequest.newBuilder().setEmail(email).build();

        return Optional.ofNullable(stub().findByEmail(request))
                .filter(GrpcFindByEmailResponse::hasUser)
                .map(GrpcFindByEmailResponse::getUser)
                .map(this::toResponse);
    }

    @Override
    public UserResponse signUpOauth2(String sub, String email, String nickname) {
        GrpcSignUpOauth2Request request = GrpcSignUpOauth2Request.newBuilder()
                .setSub(sub)
                .setEmail(email)
                .setNickname(nickname)
                .build();

        GrpcSignUpOauth2Response response = stub().signUpOauth2(request);

        return toResponse(response.getUser());
    }

    private UserServiceGrpc.UserServiceBlockingStub stub() {
        return UserServiceGrpc.newBlockingStub(channel).withDeadlineAfter(3500, TimeUnit.MILLISECONDS);
    }

    private UserResponse toResponse(GrpcUser user) {
        return UserResponse.builder()
                .id(user.getId())
                .sub(user.getSub())
                .nickname(user.getNickname())
                .email(user.getEmail())
                .roles(user.getRolesList())
                .createdAt(user.hasCreatedAt()
                        ? ServiceTimeConverter.toLocalDateTime(
                                Instant.ofEpochSecond(user.getCreatedAt().getSeconds(), user.getCreatedAt().getNanos()))
                        : null)
                .build();
    }
}
