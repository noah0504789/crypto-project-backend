package org.example.user.adapter.out.persistence;

import org.example.user.application.port.out.UserRepositoryPort;
import org.example.user.domain.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, Long>, UserRepositoryPort {

    Optional<User> findByPublicId(UUID publicId);

    @Query("""
        select distinct u
        from User u
        left join fetch u.roles ur
        left join fetch ur.role
        where u.email = :email
    """)
    Optional<User> findByEmailWithRoles(@Param("email") String email);
}
