package org.example.common.validation;

import jakarta.validation.ConstraintViolation;
import org.springframework.validation.FieldError;

public record FieldErrorDetail(
        String field,
        String path,
        String code,
        String message
) {

    private static final String UNKNOWN_FIELD = "_";

    public static FieldErrorDetail fromFieldError(FieldError error) {
        String path = normalizePath(error.getField());

        return new FieldErrorDetail(
                extractFieldName(path),
                path,
                error.getCode(),
                error.getDefaultMessage()
        );
    }

    public static FieldErrorDetail fromConstraintViolation(ConstraintViolation<?> violation) {
        String path = normalizePath(violation.getPropertyPath().toString());

        return new FieldErrorDetail(
                extractFieldName(path),
                path,
                extractConstraintCode(violation),
                violation.getMessage()
        );
    }

    private static String normalizePath(String propertyPath) {
        if (propertyPath == null || propertyPath.isBlank()) {
            return UNKNOWN_FIELD;
        }

        return propertyPath;
    }

    private static String extractFieldName(String propertyPath) {
        String normalizedPath = normalizePath(propertyPath);

        int lastDotIndex = normalizedPath.lastIndexOf('.');

        if (lastDotIndex < 0) {
            return normalizedPath;
        }

        return normalizedPath.substring(lastDotIndex + 1);
    }

    private static String extractConstraintCode(ConstraintViolation<?> violation) {
        return violation.getConstraintDescriptor()
                .getAnnotation()
                .annotationType()
                .getSimpleName();
    }
}