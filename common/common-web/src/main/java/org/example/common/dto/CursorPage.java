package org.example.common.dto;

import java.util.List;

public record CursorPage<T>(
    List<T> items,
    boolean hasNext
) {

    public static <T> CursorPage<T> empty() {
        return new CursorPage<>(List.of(), false);
    }

    public static <T> CursorPage<T> of(List<T> items, boolean hasNext) {
        return new CursorPage<>(items, hasNext);
    }
}