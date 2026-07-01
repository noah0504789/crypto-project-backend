package org.example.user.account.application.port.in;

import org.example.user.account.application.service.command.SignUpLocalCommand;
import org.example.user.account.application.service.command.SignUpOauth2Command;
import org.example.user.account.application.service.command.UpdateProfileCommand;
import org.example.user.account.domain.model.User;

public interface UserCommandUseCase {

    void updateProfile(UpdateProfileCommand command);

    User signUpOauth2(SignUpOauth2Command command);

    User signUpLocal(SignUpLocalCommand command);
}