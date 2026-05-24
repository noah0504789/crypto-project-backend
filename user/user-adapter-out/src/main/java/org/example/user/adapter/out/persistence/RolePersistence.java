package org.example.user.adapter.out.persistence;

import org.example.user.domain.model.RoleEnum;
import org.example.user.application.port.out.RolePersistencePort;
import org.example.user.domain.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RolePersistence extends JpaRepository<Role, Long>, RolePersistencePort {

    Optional<Role> findByName(RoleEnum name);
}
