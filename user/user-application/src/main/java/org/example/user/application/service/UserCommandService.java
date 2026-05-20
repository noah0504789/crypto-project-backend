package org.example.user.application.service;

import lombok.RequiredArgsConstructor;
import org.example.user.application.port.RoleRepositoryPort;
import org.example.user.application.port.UserRepositoryPort;
import org.example.user.model.domain.Role;
import org.example.user.model.domain.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserCommandService {

    private final UserRepositoryPort userRepository;
    private final RoleRepositoryPort roleRepository;

    @Transactional
    public User save(User user) {
        return userRepository.save(user);
    }

    @Transactional
    public Role saveRole(Role role) {
        return roleRepository.save(role);
    }
}
