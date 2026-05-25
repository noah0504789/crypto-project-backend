package org.example.oauth2.authorizationserver.user.application.port.out;

import org.example.contract.user.UserResponse;

import java.util.Optional;

public interface UserClientPort {

    Optional<UserResponse> findByEmail(String email);
}
