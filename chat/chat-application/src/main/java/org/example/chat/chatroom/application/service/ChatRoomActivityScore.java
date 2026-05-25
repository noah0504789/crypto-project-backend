package org.example.chat.chatroom.application.service;

public final class ChatRoomActivityScore {

    public static final long UNREAD_BOOST = 100_000_000_000_000L;

    private ChatRoomActivityScore() {
    }

    public static long calculate(long lastMsgCreatedAt, boolean unread) {
        return lastMsgCreatedAt + (unread ? UNREAD_BOOST : 0);
    }

    public static long rescoreKeepingUnreadState(long score, long fallbackMsgCreatedAt) {
        return fallbackMsgCreatedAt + (isUnread(score) ? UNREAD_BOOST : 0);
    }

    private static boolean isUnread(long score) {
        return score >= UNREAD_BOOST;
    }
}
