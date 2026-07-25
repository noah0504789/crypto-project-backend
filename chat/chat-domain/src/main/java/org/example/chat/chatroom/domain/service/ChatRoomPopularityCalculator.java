package org.example.chat.chatroom.domain.service;

import org.example.chat.chatroom.domain.model.ChatRoom;

/**
 * 채팅방 상태 → 인기도(popularity) 점수 산식.
 *
 * <p>Redis 인기방 zset(`CHAT_ROOM_POPULAR_BY_CATEGORY_INDEX`) 정렬 스코어의 단일 정의처다.
 * 현재 산식은 메시지 수(msgCnt) 단일 항이며, Mongo `idx_category_msgCnt`(저장 필드 `msgCnt`
 * DB 정렬)와 일치를 유지한다. 최근성·멤버 수 등 항을 추가할 때는 이 클래스에만 반영하되,
 * Mongo 정렬은 DB 필드 기준이라 산식을 바꾸면 Redis zset과 Mongo 정렬이 갈라진다 —
 * 정렬 소스 일관성을 함께 검토한다.
 */
public final class ChatRoomPopularityCalculator {

    private static final double MSG_COUNT_WEIGHT = 1.0;

    private ChatRoomPopularityCalculator() {
    }

    public static double calculate(ChatRoom chatRoom) {
        if (chatRoom == null) {
            return 0;
        }

        long count = chatRoom.getMsgCnt() == null ? 0L : chatRoom.getMsgCnt();

        return count * MSG_COUNT_WEIGHT;
    }
}
