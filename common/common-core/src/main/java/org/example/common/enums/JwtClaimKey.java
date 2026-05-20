package org.example.common.enums;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum JwtClaimKey {

    USER_ID("id"),
    ROLES("roles");

    private final String value;

    public String value() {
        return value;
    }
}
