package org.example.user.application.service;

import lombok.RequiredArgsConstructor;
import org.example.common.jpa.annotation.ReadReplica;
import org.example.user.application.port.out.RoleRepositoryPort;
import org.example.user.application.port.out.UserRepositoryPort;
import org.example.user.domain.model.RoleEnum;
import org.example.user.domain.model.Role;
import org.example.user.domain.model.User;
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
