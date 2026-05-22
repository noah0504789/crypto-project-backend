package org.example.chatroom.application.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = UniqueChatRoomTitleValidator.class)
public @interface UniqueChatRoomTitle {

    String message() default "{chatroom.title.unique}";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
