package org.example.user.role.adapter.out;

import org.example.user.role.domain.model.RoleEnum;
import org.example.user.role.application.port.out.RolePersistencePort;
import org.example.user.role.domain.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RolePersistenceAdapter extends JpaRepository<Role, Long>, RolePersistencePort {

    Optional<Role> findByName(RoleEnum name);
}
