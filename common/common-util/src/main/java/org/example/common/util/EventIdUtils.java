package org.example.common.util;

import com.github.f4b6a3.ulid.UlidCreator;

public final class EventIdUtils {

    private EventIdUtils() {
    }

    public static String generateId() {
        return UlidCreator.getMonotonicUlid().toString();
    }

    public static String generateTxId() {
        return generateId();
    }
}
