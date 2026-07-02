package org.example.chat.chatroom.application.service.query;

public record ListMyChatRoomsQuery(
        String memberId,
        String lastId,
        Boolean lastUnreadFlag,
        Long lastMsgCreatedAt,
        int limit
) {

    public static ListMyChatRoomsQuery firstPage(String memberId, int limit) {
        return new ListMyChatRoomsQuery(memberId, null, null, null, limit);
    }

    public static ListMyChatRoomsQuery nextPage(
        String memberId,
        String lastId,
        Boolean lastUnreadFlag,
        Long lastMsgCreatedAt,
        int limit
    ) {
        return new ListMyChatRoomsQuery(memberId, lastId, lastUnreadFlag, lastMsgCreatedAt, limit);
    }

    public boolean firstPage() {
        return lastId == null || lastId.isBlank();
    }

    public long cursorLastMsgCreatedAt() {
        return lastMsgCreatedAt == null ? 0L : lastMsgCreatedAt;
    }

    public boolean cursorUnread() {
        return Boolean.TRUE.equals(lastUnreadFlag);
    }
}
