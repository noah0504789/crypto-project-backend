package org.example.user.account.application.port.in;

import org.example.user.account.domain.model.User;

import java.util.Optional;
import java.util.UUID;

public interface UserQueryUseCase {

    Optional<User> findByPublicId(UUID publicId);

    Optional<User> findByEmailWithRoles(String email);
}
