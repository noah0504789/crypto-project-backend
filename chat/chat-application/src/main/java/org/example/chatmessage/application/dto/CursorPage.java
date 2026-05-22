package org.example.chatmessage.application.dto;

import java.util.List;

public record CursorPage<T> (
        List<T> items,
        boolean hasNext) {
}
