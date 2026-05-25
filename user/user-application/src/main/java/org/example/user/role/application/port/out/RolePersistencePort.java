package org.example.user.role.application.port.out;

import org.example.user.role.domain.model.RoleEnum;
import org.example.user.role.domain.model.Role;

import java.util.Optional;

public interface RolePersistencePort {

    Optional<Role> findByName(RoleEnum name);
}
