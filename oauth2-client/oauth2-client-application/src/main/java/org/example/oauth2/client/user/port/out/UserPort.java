package org.example.oauth2.client.user.port.out;

import org.example.contract.user.UserResponse;

import java.util.Optional;

public interface UserPort {

    Optional<UserResponse> findByEmail(String email);

    UserResponse signUpOauth2(String sub, String email, String nickname);
}
