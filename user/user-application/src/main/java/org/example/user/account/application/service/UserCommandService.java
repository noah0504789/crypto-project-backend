package org.example.user.account.application.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.example.user.account.application.port.out.UserPersistencePort;
import org.example.user.account.domain.exception.UserNotFoundException;
import org.example.user.account.domain.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserCommandService {

    private final UserPersistencePort userRepository;

    @Transactional
    public User save(User user) {
        return userRepository.save(user);
    }

    @Transactional
    public void updateProfile(UUID publicId, String nickname) {
        User user = userRepository.findByPublicId(publicId)
                .orElseThrow(() -> new UserNotFoundException(publicId));

        user.updateNickname(nickname);
    }
}
