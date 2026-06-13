package org.example.user.account.application.port.out;

import org.example.user.account.domain.model.User;

import java.util.Optional;
import java.util.UUID;

public interface UserPersistencePort {

    User save(User user);

    Optional<User> findByPublicId(UUID publicId);

    Optional<User> findByEmailWithRoles(String email);

    boolean existsByNickname(String nickname);
}
