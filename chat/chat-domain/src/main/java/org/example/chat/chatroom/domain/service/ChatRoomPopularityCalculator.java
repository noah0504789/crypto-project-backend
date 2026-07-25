package org.example.chat.chatroom.domain.service;

/**
 * 채팅방 상태 → 인기도(popularity) 점수 산식.
 *
 * <p>Redis 인기방 zset(`CHAT_ROOM_POPULAR_BY_CATEGORY_INDEX`) 정렬 스코어로 쓰인다.
 * 현재 산식은 메시지 수(msgCnt) 단일 항이며, Mongo `idx_category_msgCnt` 정렬과
 * 일치를 유지한다. 최근성·멤버 수 등 항을 추가할 때는 이 클래스에만 반영하고
 * Mongo 정렬 기준과의 일관성을 함께 검토한다.
 */
public final class ChatRoomPopularityCalculator {

    private static final double MSG_COUNT_WEIGHT = 1.0;

    private ChatRoomPopularityCalculator() {
    }

    public static double calculate(Long msgCnt) {
        long count = msgCnt == null ? 0L : msgCnt;

        return count * MSG_COUNT_WEIGHT;
    }
}
