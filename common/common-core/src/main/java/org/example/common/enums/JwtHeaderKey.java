package org.example.common.enums;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum JwtHeaderKey {

    DEFAULT_ALGORITHM("RS256"),
    DEFAULT_TYPE("JWT");

    private final String value;

    public String value() {
        return value;
    }
}
