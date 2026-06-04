package org.example.common.event;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class TypedPayload {

    private final Map<TypedKey<?>, Object> values;

    private TypedPayload(Map<TypedKey<?>, Object> values) {
        this.values = Map.copyOf(values);
    }

    public static Builder builder() {
        return new Builder();
    }

    public <T> T get(TypedKey<T> key) {
        Objects.requireNonNull(key, "key must not be null");

        return key.cast(values.get(key));
    }

    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();

        for (Map.Entry<TypedKey<?>, Object> entry : values.entrySet()) {
            result.put(entry.getKey().name(), entry.getValue());
        }

        return Map.copyOf(result);
    }

    public static final class Builder {

        private final Map<TypedKey<?>, Object> values = new LinkedHashMap<>();

        private Builder() {
        }

        public <T> Builder put(TypedKey<T> key, T value) {
            Objects.requireNonNull(key, "key must not be null");

            if (value != null) {
                key.type().cast(value);
            }

            values.put(key, value);
            return this;
        }

        public <T> Builder putIfNotNull(TypedKey<T> key, T value) {
            if (value != null) {
                put(key, value);
            }

            return this;
        }

        public TypedPayload build() {
            return new TypedPayload(values);
        }
    }
}