package org.example.user.account.application.port.in;

import org.example.user.account.domain.model.User;

import java.util.UUID;

public interface UserCommandUseCase {

    void updateProfile(UUID publicId, String nickname);

    User signUpOauth2(String sub, String email, String nickname);

    User signUpLocal(String email, String nickname, String password);
}