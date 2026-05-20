package org.example.common.enums;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum AuthTokenKey {

    ACCESS_TOKEN_QUERY("access_token"),
    BEARER_PREFIX("Bearer "),
    REFRESH_TOKEN_COOKIE("refreshToken");

    private final String value;

    public String value() {
        return value;
    }
}
