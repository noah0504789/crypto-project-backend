package org.example.common.validation;

import jakarta.validation.ConstraintViolation;
import org.springframework.validation.FieldError;

public record FieldErrorDetail(
        String field,
        String code,
        String message
) {

    public static FieldErrorDetail fromFieldError(FieldError error) {
        return new FieldErrorDetail(
                extractFieldName(error.getField()),
                error.getCode(),
                error.getDefaultMessage()
        );
    }

    public static FieldErrorDetail fromConstraintViolation(ConstraintViolation<?> violation) {
        return new FieldErrorDetail(
                extractFieldName(violation),
                extractConstraintCode(violation),
                violation.getMessage()
        );
    }

    private static String extractFieldName(ConstraintViolation<?> violation) {
        String propertyPath = violation.getPropertyPath().toString();

        return extractFieldName(propertyPath);
    }

    private static String extractFieldName(String propertyPath) {
        if (propertyPath == null || propertyPath.isBlank()) {
            return "_";
        }

        int lastDotIndex = propertyPath.lastIndexOf('.');

        if (lastDotIndex < 0) {
            return propertyPath;
        }

        return propertyPath.substring(lastDotIndex + 1);
    }

    private static String extractConstraintCode(ConstraintViolation<?> violation) {
        return violation.getConstraintDescriptor()
                .getAnnotation()
                .annotationType()
                .getSimpleName();
    }
}
