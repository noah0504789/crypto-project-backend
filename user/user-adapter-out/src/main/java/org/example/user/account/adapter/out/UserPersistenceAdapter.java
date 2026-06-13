package org.example.user.account.adapter.out;

import org.example.user.account.application.port.out.UserPersistencePort;
import org.example.user.account.domain.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserPersistenceAdapter extends JpaRepository<User, Long>, UserPersistencePort {

    Optional<User> findByPublicId(UUID publicId);

    @Query("""
        select distinct u
        from User u
        left join fetch u.roles ur
        left join fetch ur.role
        where u.email = :email
    """)
    Optional<User> findByEmailWithRoles(@Param("email") String email);

    boolean existsByNickname(String nickname);
}
