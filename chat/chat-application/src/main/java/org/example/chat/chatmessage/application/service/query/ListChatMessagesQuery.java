package org.example.chat.chatmessage.application.service.query;

public record ListChatMessagesQuery(
        String roomId,
        String myUserId,
        String lastMsgId,
        Long lastCreatedAtMs,
        int limit
) {

    public static ListChatMessagesQuery firstPage(String roomId, String myUserId, int limit) {
        return new ListChatMessagesQuery(roomId, myUserId, null, null, limit);
    }

    public static ListChatMessagesQuery prevPage(
        String roomId,
        String myUserId,
        String lastMsgId,
        Long lastCreatedAtMs,
        int limit
    ) {
        return new ListChatMessagesQuery(roomId, myUserId, lastMsgId, lastCreatedAtMs, limit);
    }

    public boolean hasNoCursor() {
        return lastMsgId == null || lastMsgId.isBlank();
    }

    public long cursorCreatedAtMs() {
        return lastCreatedAtMs == null ? 0L : lastCreatedAtMs;
    }
}