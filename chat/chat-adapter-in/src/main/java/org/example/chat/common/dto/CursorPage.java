package org.example.chat.common.dto;

import java.util.List;

public record CursorPage<T> (
        List<T> items,
        boolean hasNext) {
}
