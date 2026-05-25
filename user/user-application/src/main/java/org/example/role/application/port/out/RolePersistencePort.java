package org.example.role.application.port.out;

import org.example.role.domain.model.RoleEnum;
import org.example.role.domain.model.Role;

import java.util.Optional;

public interface RolePersistencePort {

    Optional<Role> findByName(RoleEnum name);
}
