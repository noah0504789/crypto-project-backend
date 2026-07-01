package org.example.user.account.application.port.in;

import org.example.user.account.application.service.command.UpdateProfileCommand;
import org.example.user.account.domain.model.User;

public interface UserCommandUseCase {

    void updateProfile(UpdateProfileCommand command);

    User signUpOauth2(String sub, String email, String nickname);

    User signUpLocal(String email, String nickname, String password);
}