package org.example.user.role.adapter.out;

import lombok.RequiredArgsConstructor;
import org.example.user.role.domain.model.RoleEnum;
import org.example.user.role.application.port.out.RolePersistencePort;
import org.example.user.role.domain.model.Role;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class JpaRoleAdapter implements RolePersistencePort {

    private final JpaRoleRepository roleRepository;

    @Override
    public Optional<Role> findByName(RoleEnum name) {
        return roleRepository.findByName(name)
                .map(JpaRole::toDomain);
    }
}
