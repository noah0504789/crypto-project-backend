package org.example.user.account.application.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.RequiredArgsConstructor;
import org.example.user.account.application.port.out.UserPersistencePort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UniqueUserNicknameValidator implements ConstraintValidator<UniqueUserNickname, String> {

    private final UserPersistencePort userPersistencePort;

    @Override
    public boolean isValid(String nickname, ConstraintValidatorContext context) {
        if (nickname == null || nickname.isBlank()) {
            return true;
        }

        return !userPersistencePort.existsByNickname(nickname);
    }
}