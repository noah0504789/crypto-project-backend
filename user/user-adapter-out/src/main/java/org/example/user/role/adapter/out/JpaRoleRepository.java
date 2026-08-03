package org.example.user.role.adapter.out;

import org.example.user.role.domain.model.RoleEnum;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JpaRoleRepository extends JpaRepository<JpaRole, Long> {

    Optional<JpaRole> findByName(RoleEnum name);
}
