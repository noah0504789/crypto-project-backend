package org.example.common.dto;

import java.util.List;
import java.util.function.Function;

public final class CursorPages {

    private CursorPages() {
    }

    public static <T, R> CursorPage<R> from(
        List<T> items,
        int limit,
        Function<T, R> mapper
    ) {
        if (items.isEmpty()) {
            return CursorPage.empty();
        }

        boolean hasNext = items.size() > limit;

        List<T> pageItems = hasNext ? items.subList(0, limit) : items;

        return CursorPage.of(
                pageItems.stream()
                        .map(mapper)
                        .toList(),
                hasNext
        );
    }
}