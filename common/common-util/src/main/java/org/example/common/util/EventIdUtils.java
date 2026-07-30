package org.example.common.util;

import com.github.f4b6a3.ulid.UlidCreator;

import java.util.UUID;

public final class EventIdUtils {

    private EventIdUtils() {
    }

    public static String generateUlid() {
        return UlidCreator.getMonotonicUlid().toString();
    }

    public static String generateUUID() {
        return UUID.randomUUID().toString();
    }

    public static String generateTxId() {
        return generateUlid();
    }
}
