package org.example.user.account.application.service;

import lombok.RequiredArgsConstructor;
import org.example.user.account.application.port.in.UserCommandUseCase;
import org.example.user.account.application.port.out.UserPersistencePort;
import org.example.user.account.application.service.command.SignUpLocalCommand;
import org.example.user.account.application.service.command.SignUpOauth2Command;
import org.example.user.account.application.service.command.UpdateProfileCommand;
import org.example.user.account.application.exception.UserNotFoundException;
import org.example.user.account.domain.model.User;
import org.example.user.role.application.port.out.RolePersistencePort;
import org.example.user.role.application.exception.RoleNotFoundException;
import org.example.user.role.domain.model.Role;
import org.example.user.role.domain.model.RoleEnum;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserCommandService implements UserCommandUseCase {

    private final UserPersistencePort userRepository;
    private final RolePersistencePort roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void updateProfile(UpdateProfileCommand command) {
        UUID publicId = command.publicId();
        User user = userRepository.findByPublicId(publicId)
                .orElseThrow(() -> new UserNotFoundException(publicId));

        user.updateNickname(command.nickname());

        userRepository.save(user);
    }

    @Override
    @Transactional
    public User signUpOauth2(SignUpOauth2Command command) {
        Role role = getDefaultRole();

        User newUser = User.ofOAuth2(
                command.sub(),
                command.email(),
                command.nickname()
        );

        newUser.addRole(role);

        return userRepository.save(newUser);
    }

    @Override
    @Transactional
    public User signUpLocal(SignUpLocalCommand command) {
        Role role = getDefaultRole();

        String encodedPassword = passwordEncoder.encode(command.password());

        User newUser = User.ofLocal(
                command.email(),
                command.nickname(),
                encodedPassword
        );

        newUser.addRole(role);

        return userRepository.save(newUser);
    }

    private Role getDefaultRole() {
        RoleEnum defaultRole = User.getDefaultRole();

        return roleRepository.findByName(defaultRole)
                .orElseThrow(() -> new RoleNotFoundException(defaultRole));
    }
}