package org.example.user.account.application.service;

import lombok.RequiredArgsConstructor;
import org.example.user.account.application.port.out.UserPersistencePort;
import org.example.user.account.domain.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserQueryService {

    private final UserPersistencePort userPersistencePort;

    @Transactional(readOnly = true)
    public Optional<User> findByPublicId(UUID publicId) {
        return userPersistencePort.findByPublicId(publicId);
    }

    @Transactional(readOnly = true)
    public Optional<User> findByEmailWithRoles(String email) {
        return userPersistencePort.findByEmailWithRoles(email);
    }
}
