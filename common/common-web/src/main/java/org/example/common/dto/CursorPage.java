package org.example.common.dto;

import java.util.List;

public record CursorPage<T> (
        List<T> items,
        boolean hasNext) {
}
