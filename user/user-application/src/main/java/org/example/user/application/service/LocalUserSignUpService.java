package org.example.user.application.service;

import lombok.RequiredArgsConstructor;
import org.example.role.application.service.RoleQueryService;
import org.example.role.domain.exception.RoleNotFoundException;
import org.example.role.domain.model.RoleEnum;
import org.example.role.domain.model.Role;
import org.example.user.domain.model.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LocalUserSignUpService {

    private final RoleQueryService roleQueryService;
    private final UserCommandService userCommandService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void signUp(String email, String nickname, String password) {
        RoleEnum defaultRole = User.getDefaultRole();

        Role role = roleQueryService.findByName(defaultRole)
                .orElseThrow(() -> new RoleNotFoundException(defaultRole));

        String encodedPassword = passwordEncoder.encode(password);

        User user = User.ofLocal(email, nickname, encodedPassword);
        user.addRole(role);

        userCommandService.save(user);
    }
}