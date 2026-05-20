package org.example.user.application.port;

import org.example.user.model.RoleEnum;
import org.example.user.model.domain.Role;

import java.util.Optional;

public interface RoleRepositoryPort {

    Role save(Role role);

    Optional<Role> findByName(RoleEnum name);
}
