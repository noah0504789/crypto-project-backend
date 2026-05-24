package org.example.user.application.port.out;

import org.example.user.domain.model.RoleEnum;
import org.example.user.domain.model.Role;

import java.util.Optional;

public interface RolePersistencePort {

    Optional<Role> findByName(RoleEnum name);
}
