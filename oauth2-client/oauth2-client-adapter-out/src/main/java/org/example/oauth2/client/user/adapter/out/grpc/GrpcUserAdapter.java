package org.example.oauth2.client.user.adapter.out.grpc;

import lombok.RequiredArgsConstructor;
import org.example.contract.user.UserResponse;
import org.example.oauth2.client.user.port.out.UserPort;
import org.example.user.client.UserClient;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GrpcUserAdapter implements UserPort {

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
