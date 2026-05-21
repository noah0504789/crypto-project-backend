package org.example.user.client;

import org.example.contract.user.UserResponse;

import java.util.Optional;

public interface UserClient {

    Optional<UserResponse> findByEmail(String email);

    UserResponse signUpOauth2(String sub, String email, String nickname);
}
