package org.example.user.application.port;

import org.example.user.model.domain.User;

import java.util.Optional;
import java.util.UUID;

public interface UserRepositoryPort {

    User save(User user);

    Optional<User> findByPublicId(UUID publicId);

    Optional<User> findByEmailWithRoles(String email);
}
