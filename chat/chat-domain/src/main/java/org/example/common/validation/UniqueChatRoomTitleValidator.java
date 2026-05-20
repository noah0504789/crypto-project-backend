package org.example.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.RequiredArgsConstructor;
import org.example.chatroom.application.port.in.ChatRoomQueryUseCase;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UniqueChatRoomTitleValidator implements ConstraintValidator<UniqueChatRoomTitle, String> {

    private final ChatRoomQueryUseCase chatRoomQueryUseCase;

    @Override
    public boolean isValid(String title, ConstraintValidatorContext context) {
        if (title == null || title.isBlank()) return true;
        if (!chatRoomQueryUseCase.existsByTitle(title)) return true;

        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode("title")
                .addConstraintViolation();

        return false;
    }
}
