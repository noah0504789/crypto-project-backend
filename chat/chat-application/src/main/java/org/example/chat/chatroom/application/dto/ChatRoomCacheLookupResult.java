package org.example.chat.chatroom.application.dto;

import org.example.chat.chatroom.domain.model.ChatRoom;

import java.util.List;

public record ChatRoomCacheLookupResult(
        List<String> orderedIds,
        List<ChatRoom> hits,
        List<String> misses
) {

    public ChatRoomCacheLookupResult {
        orderedIds = orderedIds == null ? List.of() : List.copyOf(orderedIds);
        hits = hits == null ? List.of() : List.copyOf(hits);
        misses = misses == null ? List.of() : List.copyOf(misses);
    }

    public boolean hasNoIndex() {
        return orderedIds.isEmpty();
    }

    public boolean isAllHit() {
        return !orderedIds.isEmpty() && misses.isEmpty();
    }

    public boolean hasMisses() {
        return !misses.isEmpty();
    }
}