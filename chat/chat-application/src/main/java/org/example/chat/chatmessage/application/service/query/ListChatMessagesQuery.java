package org.example.chat.chatmessage.application.service.query;

public record ListChatMessagesQuery(
        String roomId,
        String lastId,
        Long lastCreatedAtMillis,
        int limit
) {

    public static ListChatMessagesQuery firstPage(String roomId, int limit) {
        return new ListChatMessagesQuery(roomId, null, null, limit);
    }

    public static ListChatMessagesQuery prevPage(
        String roomId,
        String lastId,
        Long lastCreatedAtMillis,
        int limit
    ) {
        return new ListChatMessagesQuery(roomId, lastId, lastCreatedAtMillis, limit);
    }

    public boolean firstPage() {
        return lastId == null || lastId.isBlank();
    }

    public long cursorCreatedAtMillis() {
        return lastCreatedAtMillis == null ? 0L : lastCreatedAtMillis;
    }
}