package org.example.common.enums;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum RoleKey {

    PREFIX("ROLE_"),
    DEFAULT_USER("ROLE_USER"),
    REQUIRED_USER("USER");

    private final String value;

    public String value() {
        return value;
    }
}
