package org.example.chatmessage.adapter.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

public record CursorPage<T> (
        List<T> items,
        boolean hasNext) {
}
