package org.example.user.application.service;

import lombok.RequiredArgsConstructor;
import org.example.user.model.RoleEnum;
import org.example.user.model.domain.Role;
import org.example.user.model.domain.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class Oauth2UserSignUpService {

    private final UserQueryService userQueryService;
    private final UserCommandService userCommandService;

    @Transactional
    public User signUp(String sub, String email, String nickname) {
        RoleEnum defaultRole = User.getDefaultRole();

        Role role = userQueryService.findRoleByName(defaultRole)
                .orElseGet(() -> userCommandService.saveRole(Role.ofName(defaultRole)));

        User newUser = User.ofOAuth2(sub, email, nickname, role);

        return userCommandService.save(newUser);
    }
}