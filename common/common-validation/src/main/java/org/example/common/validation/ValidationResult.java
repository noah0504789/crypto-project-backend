package org.example.common.validation;

import jakarta.validation.ConstraintViolation;
import org.springframework.validation.Errors;

import java.util.List;
import java.util.Set;

public record ValidationResult(List<FieldErrorDetail> errors) {

    public static ValidationResult fromBindingErrors(Errors errors) {
        return new ValidationResult(
                errors.getFieldErrors().stream()
                        .map(FieldErrorDetail::fromFieldError)
                        .toList()
        );
    }

    public static ValidationResult fromConstraintViolations(Set<? extends ConstraintViolation<?>> violations) {
        return new ValidationResult(
                violations.stream()
                        .map(FieldErrorDetail::fromConstraintViolation)
                        .toList()
        );
    }
}
