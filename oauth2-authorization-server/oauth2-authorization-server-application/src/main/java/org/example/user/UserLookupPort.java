package org.example.user;

import java.util.Optional;

public interface UserLookupPort {

    Optional<UserResponse> findByEmail(String email);
}
