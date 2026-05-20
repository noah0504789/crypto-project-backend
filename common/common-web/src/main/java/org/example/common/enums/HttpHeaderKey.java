package org.example.common.enums;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum HttpHeaderKey {

    USER_ID("X-User-Id");

    public static final String USER_ID_VALUE = "X-User-Id";

    private final String value;

    public String value() {
        return value;
    }
}
