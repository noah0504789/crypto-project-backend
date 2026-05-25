package org.example.user.account.application.service;

import lombok.RequiredArgsConstructor;
import org.example.user.account.application.port.out.UserPersistencePort;
import org.example.user.account.domain.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserCommandService {

    private final UserPersistencePort userRepository;

    @Transactional
    public User save(User user) {
        return userRepository.save(user);
    }
}
