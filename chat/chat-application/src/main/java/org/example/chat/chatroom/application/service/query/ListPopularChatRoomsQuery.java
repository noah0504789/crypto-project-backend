package org.example.chat.chatroom.application.service.query;

import org.example.chat.chatroom.domain.model.ChatRoomCategory;

public record ListPopularChatRoomsQuery(
        ChatRoomCategory category,
        String lastRoomId,
        Long lastPopularity,
        int limit
) {

    public static ListPopularChatRoomsQuery firstPage(ChatRoomCategory category, int limit) {
        return new ListPopularChatRoomsQuery(category, null, null, limit);
    }

    public static ListPopularChatRoomsQuery nextPage(
        ChatRoomCategory category,
        String lastRoomId,
        Long lastPopularity,
        int limit
    ) {
        return new ListPopularChatRoomsQuery(category, lastRoomId, lastPopularity, limit);
    }

    public boolean hasNoCursor() {
        return lastRoomId == null || lastRoomId.isBlank();
    }
}
