package org.example.user.account.application.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = UniqueUserNicknameValidator.class)
public @interface UniqueUserNickname {

    String message() default "{user.nickname.unique}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}