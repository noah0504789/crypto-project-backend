package org.example.user.application.service;

import lombok.RequiredArgsConstructor;
import org.example.user.model.RoleEnum;
import org.example.user.model.domain.Role;
import org.example.user.model.domain.User;
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
    public void signUp(LocalUserSignUpCommand request) {
        RoleEnum defaultRole = User.getDefaultRole();

        Role role = userQueryService.findRoleByName(defaultRole)
                .orElseGet(() -> userCommandService.saveRole(Role.ofName(defaultRole)));

        String encodedPassword = passwordEncoder.encode(request.password());

        User user = User.ofLocal(request.email(), request.nickname(), encodedPassword, role);

        userCommandService.save(user);
    }
}
