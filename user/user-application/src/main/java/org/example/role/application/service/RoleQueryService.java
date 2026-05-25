package org.example.role.application.service;

import lombok.RequiredArgsConstructor;
import org.example.common.jpa.annotation.ReadReplica;
import org.example.role.application.port.out.RolePersistencePort;
import org.example.role.domain.model.Role;
import org.example.role.domain.model.RoleEnum;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RoleQueryService {

    private final RolePersistencePort rolePersistencePort;

    @ReadReplica
    @Transactional(readOnly = true)
    public Optional<Role> findByName(RoleEnum role) {
        return rolePersistencePort.findByName(role);
    }
}