package org.example.infra.grpc;

import lombok.RequiredArgsConstructor;
import org.example.contract.user.UserResponse;
import org.example.user.UserLookupPort;
import org.example.user.client.UserClient;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserClientAdapter implements UserLookupPort {

    private final UserClient userClient;

    @Override
    public Optional<UserResponse> findByEmail(String email) {
        return userClient.findByEmail(email);
    }
}
