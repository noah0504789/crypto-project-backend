package org.example.user.port.out;

import org.example.contract.user.UserResponse;

import java.util.Optional;

public interface UserClientPort {

    Optional<UserResponse> findByEmail(String email);

    UserResponse signUpOauth2(String sub, String email, String nickname);
}
