package org.example.user.account.application.service;

import lombok.RequiredArgsConstructor;
import org.example.user.account.application.port.in.UserQueryUseCase;
import org.example.user.account.application.port.out.UserPersistencePort;
import org.example.user.account.domain.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserQueryService implements UserQueryUseCase {

    private final UserPersistencePort userPersistencePort;

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByPublicId(UUID publicId) {
        return userPersistencePort.findByPublicId(publicId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByEmailWithRoles(String email) {
        return userPersistencePort.findByEmailWithRoles(email);
    }
}
