package org.example.user.account.application.service;

import lombok.RequiredArgsConstructor;
import org.example.user.account.application.port.in.UserCommandUseCase;
import org.example.user.account.application.port.out.UserPersistencePort;
import org.example.user.account.domain.exception.UserNotFoundException;
import org.example.user.account.domain.model.User;
import org.example.user.role.application.port.out.RolePersistencePort;
import org.example.user.role.domain.exception.RoleNotFoundException;
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
    public void updateProfile(UUID publicId, String nickname) {
        User user = userRepository.findByPublicId(publicId)
                .orElseThrow(() -> new UserNotFoundException(publicId));

        user.updateNickname(nickname);
    }

    @Override
    @Transactional
    public User signUpOauth2(String sub, String email, String nickname) {
        Role role = getDefaultRole();

        User newUser = User.ofOAuth2(sub, email, nickname);
        newUser.addRole(role);

        return userRepository.save(newUser);
    }

    @Override
    @Transactional
    public User signUpLocal(String email, String nickname, String password) {
        Role role = getDefaultRole();

        String encodedPassword = passwordEncoder.encode(password);

        User newUser = User.ofLocal(email, nickname, encodedPassword);
        newUser.addRole(role);

        return userRepository.save(newUser);
    }

    private Role getDefaultRole() {
        RoleEnum defaultRole = User.getDefaultRole();

        return roleRepository.findByName(defaultRole)
                .orElseThrow(() -> new RoleNotFoundException(defaultRole));
    }
}