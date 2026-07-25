package org.example.chat.chatroom.domain.service;

import org.example.chat.chatroom.domain.model.ChatRoom;

/**
 * 채팅방 상태 → 인기도(popularity) 점수 산식의 단일 정의처.
 *
 * <p>인기도는 Redis 인기방 zset(`CHAT_ROOM_POPULAR_BY_CATEGORY_INDEX`)에 미리 계산해 저장하는
 * materialized score다. 유지 경로가 둘이라 산식이 두 메서드에 걸린다:
 * <ul>
 *   <li>{@link #calculate(ChatRoom)} — 절대값. warm-up/복구 시 `ZADD`로 전체 재계산.</li>
 *   <li>{@link #messageDelta()} — 메시지 1건의 증분. 저장 시 `ZINCRBY`로 더하는 값
 *       ({@code RedisChatMessageAdapter} → {@code storeChatMessage.lua}).</li>
 * </ul>
 * 두 경로가 같은 가중치 상수를 읽으므로 산식을 바꿔도 절대·증분이 일치한다.
 *
 * <p>현재 산식은 메시지 수(msgCnt) 단일 항(가중치 1.0)이라 증분(ZINCRBY, 선형)으로 유지 가능하다.
 * 멤버 수 항을 추가하려면: (1) 여기에 memberCnt 가중치 + {@code calculate} 반영,
 * (2) {@code memberDelta()} 추가, (3) 입장/퇴장 어댑터에서 popular zset에 `ZINCRBY memberDelta()` 배선.
 * 단, 유지시간처럼 이벤트가 없는(시간에 따라 연속 변하는) 항은 증분으로 유지할 수 없어 주기적 재계산이 필요하다.
 *
 * <p>또한 Mongo 인기방 정렬은 DB 레벨 `sort(msgCnt)`(`idx_category_msgCnt`)로, 이 계산기를 호출하지 않는
 * 별도 결합점이다. 산식이 저장 필드 `msgCnt`와 갈라지면 Redis zset과 Mongo 정렬이 어긋난다 —
 * 산식 변경 시 이 세 곳(calculate·messageDelta·Mongo sort)을 함께 본다.
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

    public static double messageDelta() {
        return MSG_COUNT_WEIGHT;
    }
}
