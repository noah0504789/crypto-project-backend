package org.example.user.application.service;

import lombok.RequiredArgsConstructor;
import org.example.role.application.service.RoleQueryService;
import org.example.role.domain.exception.RoleNotFoundException;
import org.example.role.domain.model.RoleEnum;
import org.example.role.domain.model.Role;
import org.example.user.domain.model.User;
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