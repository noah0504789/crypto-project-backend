package org.example.user.application.port.out;

import org.example.user.domain.model.RoleEnum;
import org.example.user.domain.model.Role;

import java.util.Optional;

public interface RoleRepositoryPort {

    Role save(Role role);

    Optional<Role> findByName(RoleEnum name);
}
