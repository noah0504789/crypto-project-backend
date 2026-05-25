package org.example.user.application.service;

import lombok.RequiredArgsConstructor;
import org.example.common.jpa.annotation.ReadReplica;
import org.example.role.application.port.out.RolePersistencePort;
import org.example.user.application.port.out.UserPersistencePort;
import org.example.role.domain.model.RoleEnum;
import org.example.role.domain.model.Role;
import org.example.user.domain.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserQueryService {

    private final UserPersistencePort userPersistencePort;

    @Transactional(readOnly = true)
    public Optional<User> findByPublicId(UUID publicId) {
        return userPersistencePort.findByPublicId(publicId);
    }

    @Transactional(readOnly = true)
    public Optional<User> findByEmailWithRoles(String email) {
        return userPersistencePort.findByEmailWithRoles(email);
    }
}
