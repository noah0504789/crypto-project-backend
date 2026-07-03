package org.example.chat.chatroom.application.service.query;

public record ListMyChatRoomsQuery(
        String memberId,
        String lastMsgId,
        Boolean lastUnreadFlag,
        Long lastMsgCreatedAt,
        int limit
) {

    public static ListMyChatRoomsQuery firstPage(String memberId, int limit) {
        return new ListMyChatRoomsQuery(memberId, null, null, null, limit);
    }

    public static ListMyChatRoomsQuery nextPage(
        String memberId,
        String lastRoomId,
        Boolean lastUnreadFlag,
        Long lastMsgCreatedAt,
        int limit
    ) {
        return new ListMyChatRoomsQuery(memberId, lastRoomId, lastUnreadFlag, lastMsgCreatedAt, limit);
    }

    public boolean hasNoCursor() {
        return lastMsgId == null || lastMsgId.isBlank();
    }

    public long cursorLastMsgCreatedAt() {
        return lastMsgCreatedAt == null ? 0L : lastMsgCreatedAt;
    }

    public boolean cursorUnread() {
        return Boolean.TRUE.equals(lastUnreadFlag);
    }
}
