package org.example.user;

import lombok.RequiredArgsConstructor;
import org.example.contract.user.UserResponse;
import org.example.user.port.out.UserClientPort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserCommandService {

    private final UserClientPort userClientPort;

    public UserResponse signUpOauth2(String sub, String email, String nickname) {
        return userClientPort.signUpOauth2(sub, email, nickname);
    }
}
