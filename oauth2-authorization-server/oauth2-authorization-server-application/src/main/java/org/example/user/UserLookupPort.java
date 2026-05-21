package org.example.user;

import org.example.contract.user.UserResponse;

import java.util.Optional;

public interface UserLookupPort {

    Optional<UserResponse> findByEmail(String email);
}
