package org.example.oauth2.authorizationserver.user.adapter.out.grpc;

import lombok.RequiredArgsConstructor;
import org.example.contract.user.UserResponse;
import org.example.oauth2.authorizationserver.user.application.port.out.UserQueryPort;
import org.example.user.client.UserClient;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GrpcUserQueryAdapter implements UserQueryPort {

    private final UserClient userClient;

    @Override
    public Optional<UserResponse> findByEmail(String email) {
        return userClient.findByEmail(email);
    }
}
