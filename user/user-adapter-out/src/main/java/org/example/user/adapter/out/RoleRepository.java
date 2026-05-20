package org.example.user.adapter.out;

import org.example.user.model.RoleEnum;
import org.example.user.application.port.RoleRepositoryPort;
import org.example.user.model.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long>, RoleRepositoryPort {

    Optional<Role> findByName(RoleEnum name);
}
