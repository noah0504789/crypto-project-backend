package org.example.role.adapter.out;

import org.example.role.domain.model.RoleEnum;
import org.example.role.application.port.out.RolePersistencePort;
import org.example.role.domain.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RolePersistenceAdapter extends JpaRepository<Role, Long>, RolePersistencePort {

    Optional<Role> findByName(RoleEnum name);
}
