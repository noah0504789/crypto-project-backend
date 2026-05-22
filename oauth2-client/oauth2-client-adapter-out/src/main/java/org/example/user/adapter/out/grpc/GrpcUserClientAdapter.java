package org.example.user.adapter.out.grpc;

import lombok.RequiredArgsConstructor;
import org.example.contract.user.UserResponse;
import org.example.user.port.out.UserClientPort;
import org.example.user.client.UserClient;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GrpcUserClientAdapter implements UserClientPort {

    private final UserClient userClient;

    @Override
    public Optional<UserResponse> findByEmail(String email) {
        return userClient.findByEmail(email);
    }

    @Override
    public UserResponse signUpOauth2(String sub, String email, String nickname) {
        return userClient.signUpOauth2(sub, email, nickname);
    }
}
