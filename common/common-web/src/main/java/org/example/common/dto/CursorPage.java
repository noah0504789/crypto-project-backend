package org.example.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
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