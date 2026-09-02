package org.example.chat.chatroom.domain.service;

public final class MyChatRoomScoreCalculator {

    /** 안읽은 방을 항상 상단으로 올리는 가중치. Redis active zset score 를 만드는 Lua 도 같은 값을 쓴다. */
    public static final long UNREAD_PRIORITY_WEIGHT = 100_000_000_000_000L;

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
