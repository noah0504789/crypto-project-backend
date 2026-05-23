package org.example.user.application.service;

import lombok.RequiredArgsConstructor;
import org.example.user.domain.model.RoleEnum;
import org.example.user.domain.model.Role;
import org.example.user.domain.model.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LocalUserSignUpService {

    private final UserQueryService userQueryService;
    private final UserCommandService userCommandService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void signUp(String email, String nickname, String password) {
        RoleEnum defaultRole = User.getDefaultRole();

        Role role = userQueryService.findRoleByName(defaultRole)
                .orElseGet(() -> userCommandService.saveRole(Role.ofName(defaultRole)));

        String encodedPassword = passwordEncoder.encode(password);

        User user = User.ofLocal(email, nickname, encodedPassword, role);

        userCommandService.save(user);
    }
}
