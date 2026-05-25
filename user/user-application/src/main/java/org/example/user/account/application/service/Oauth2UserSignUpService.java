package org.example.user.account.application.service;

import lombok.RequiredArgsConstructor;
import org.example.user.role.application.service.RoleQueryService;
import org.example.user.role.domain.exception.RoleNotFoundException;
import org.example.user.role.domain.model.RoleEnum;
import org.example.user.role.domain.model.Role;
import org.example.user.account.domain.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class Oauth2UserSignUpService {

    private final RoleQueryService roleQueryService;
    private final UserCommandService userCommandService;

    @Transactional
    public User signUp(String sub, String email, String nickname) {
        RoleEnum defaultRole = User.getDefaultRole();

        Role role = roleQueryService.findByName(defaultRole)
                .orElseThrow(() -> new RoleNotFoundException(defaultRole));

        User newUser = User.ofOAuth2(sub, email, nickname);
        newUser.addRole(role);

        return userCommandService.save(newUser);
    }
}