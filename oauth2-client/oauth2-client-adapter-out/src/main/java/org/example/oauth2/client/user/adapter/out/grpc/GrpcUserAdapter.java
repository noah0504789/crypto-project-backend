package org.example.oauth2.client.user.adapter.out.grpc;

import lombok.RequiredArgsConstructor;
import org.example.common.grpc.client.GrpcFutures;
import org.example.common.time.ServiceTimeConverter;
import org.example.contract.user.UserResponse;
import org.example.grpc.user.GrpcUser;
import org.example.oauth2.client.user.port.out.UserPort;
import org.example.user.client.UserClient;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GrpcUserAdapter implements UserPort {

    private final UserClient userClient;

    @Override
    public Optional<UserResponse> findByEmail(String email) {
        return Optional.of(GrpcFutures.join(userClient.findByEmail(email)))
                .filter(response -> response.hasUser())
                .map(response -> toResponse(response.getUser()));
    }

    @Override
    public UserResponse signUpOauth2(String sub, String email, String nickname) {
        return toResponse(GrpcFutures.join(userClient.signUpOauth2(sub, email, nickname)).getUser());
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
                                Instant.ofEpochSecond(
                                        user.getCreatedAt().getSeconds(),
                                        user.getCreatedAt().getNanos()))
                        : null)
                .build();
    }
}
