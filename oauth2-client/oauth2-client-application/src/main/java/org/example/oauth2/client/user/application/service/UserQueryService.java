package org.example.oauth2.client.user.application.service;

import lombok.RequiredArgsConstructor;
import org.example.contract.user.UserResponse;
import org.example.oauth2.client.user.port.out.UserClientPort;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserQueryService {

    private final UserClientPort userClientPort;

    public Optional<UserResponse> findByEmail(String email) {
        return userClientPort.findByEmail(email);
    }
}
