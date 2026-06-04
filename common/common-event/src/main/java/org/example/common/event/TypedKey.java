package org.example.common.event;

import java.util.Objects;

public record TypedKey<T>(
        String name,
        Class<T> type
) {

    public TypedKey {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(type, "type must not be null");
    }

    public T cast(Object value) {
        if (value == null) {
            return null;
        }

        return type.cast(value);
    }
}