package org.example.user.account.adapter.out;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface JpaUserRepository extends JpaRepository<JpaUser, Long> {

    Optional<JpaUser> findByPublicId(UUID publicId);

    @Query("""
        select distinct u
        from JpaUser u
        left join fetch u.roles ur
        left join fetch ur.role
        where u.email = :email
    """)
    Optional<JpaUser> findByEmailWithRoles(@Param("email") String email);

    boolean existsByNickname(String nickname);
}
