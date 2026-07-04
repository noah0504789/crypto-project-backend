package org.example.oauth2.authorizationserver.user.application.service;

import lombok.RequiredArgsConstructor;
import org.example.contract.user.UserResponse;
import org.example.oauth2.authorizationserver.user.application.port.out.UserQueryPort;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserQueryService {

    private final UserQueryPort userQueryPort;

    public Optional<UserResponse> findByEmail(String email) {
        return userQueryPort.findByEmail(email);
    }
}
