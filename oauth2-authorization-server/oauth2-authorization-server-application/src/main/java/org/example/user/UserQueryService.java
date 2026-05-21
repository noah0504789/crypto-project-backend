package org.example.user;

import lombok.RequiredArgsConstructor;
import org.example.contract.user.UserResponse;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserQueryService {

    private final UserLookupPort userLookupPort;

    public Optional<UserResponse> findByEmail(String email) {
        return userLookupPort.findByEmail(email);
    }
}
