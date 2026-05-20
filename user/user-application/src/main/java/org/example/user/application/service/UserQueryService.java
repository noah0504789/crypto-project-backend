package org.example.user.application.service;

import lombok.RequiredArgsConstructor;
import org.example.infra.annotation.ReadReplica;
import org.example.user.application.port.RoleRepositoryPort;
import org.example.user.application.port.UserRepositoryPort;
import org.example.user.model.RoleEnum;
import org.example.user.model.domain.Role;
import org.example.user.model.domain.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserQueryService {

    private final UserRepositoryPort userRepository;
    private final RoleRepositoryPort roleRepository;

    @Transactional(readOnly = true)
    public Optional<User> findByPublicId(UUID publicId) {
        return userRepository.findByPublicId(publicId);
    }

    @Transactional(readOnly = true)
    public Optional<User> findByEmailWithRoles(String email) {
        return userRepository.findByEmailWithRoles(email);
    }

    @ReadReplica
    @Transactional(readOnly = true)
    public Optional<Role> findRoleByName(RoleEnum role) {
        return roleRepository.findByName(role);
    }
}
