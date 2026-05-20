package org.example.user;

import java.util.Optional;

public interface UserPort {

    Optional<UserResponse> findByEmail(String email);

    UserResponse signUpOauth2(String sub, String email, String nickname);
}
