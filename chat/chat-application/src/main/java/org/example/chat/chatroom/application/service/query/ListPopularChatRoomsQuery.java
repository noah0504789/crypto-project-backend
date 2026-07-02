package org.example.chat.chatroom.application.service.query;

import org.example.chat.chatroom.domain.model.ChatRoomCategory;

public record ListPopularChatRoomsQuery(
        ChatRoomCategory category,
        String lastId,
        Long lastPopularity,
        int limit
) {

    public static ListPopularChatRoomsQuery firstPage(ChatRoomCategory category, int limit) {
        return new ListPopularChatRoomsQuery(category, null, null, limit);
    }

    public static ListPopularChatRoomsQuery nextPage(
        ChatRoomCategory category,
        String lastId,
        Long lastPopularity,
        int limit
    ) {
        return new ListPopularChatRoomsQuery(category, lastId, lastPopularity, limit);
    }

    public boolean firstPage() {
        return lastId == null || lastId.isBlank();
    }
}
