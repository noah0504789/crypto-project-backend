package org.example.oauth2.client.user.application.service;

import lombok.RequiredArgsConstructor;
import org.example.contract.user.UserResponse;
import org.example.oauth2.client.user.port.out.UserPort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserCommandService {

    private final UserPort userPort;

    public UserResponse signUpOauth2(String sub, String email, String nickname) {
        return userPort.signUpOauth2(sub, email, nickname);
    }
}
