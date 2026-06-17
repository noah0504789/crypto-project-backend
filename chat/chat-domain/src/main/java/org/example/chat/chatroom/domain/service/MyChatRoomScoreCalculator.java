package org.example.chat.chatroom.domain.service;

public final class MyChatRoomScoreCalculator {

    private static final long UNREAD_PRIORITY_WEIGHT = 100_000_000_000_000L;

    private MyChatRoomScoreCalculator() {
    }

    public static long unread(long lastMsgCreatedAt) {
        return lastMsgCreatedAt + UNREAD_PRIORITY_WEIGHT;
    }

    public static long read(long lastMsgCreatedAt) {
        return lastMsgCreatedAt;
    }

    public static long rescoreKeepingUnreadState(long score, long fallbackMsgCreatedAt) {
        return fallbackMsgCreatedAt + (isUnread(score) ? UNREAD_PRIORITY_WEIGHT : 0);
    }

    private static boolean isUnread(long score) {
        return score >= UNREAD_PRIORITY_WEIGHT;
    }
}