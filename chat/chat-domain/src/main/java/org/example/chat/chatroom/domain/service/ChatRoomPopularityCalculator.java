package org.example.chat.chatroom.domain.service;

import org.example.chat.chatroom.domain.model.ChatRoom;

/**
 * 채팅방 상태 → 인기도(popularity) 점수 산식의 단일 정의처.
 *
 * <p>인기도는 Redis 인기방 zset(`CHAT_ROOM_POPULAR_BY_CATEGORY_INDEX`)에 미리 계산해 저장하는
 * materialized score다. 메시지 저장 시 실시간 증분(ZINCRBY)을 하지 않고,
 * {@code ChatRoomPopularityScheduler}가 주기적으로 category별 상위 후보에 대해 {@link #calculate(ChatRoom)}로
 * zset을 통째 재구축한다(on-read 캐시 미스 복구도 같은 경로). 즉 산식은 이 계산기 한 곳에만 있다.
 *
 * <p>현재 산식은 메시지 수(msgCnt) 단일 항(가중치 1.0). 멤버 수 등 항을 추가하려면 여기에 가중치 +
 * {@code calculate} 반영만 하면 되고, 재구축이 주기적이라 어떤 항(멤버 수·최근성·유지시간 등)이든 수용한다.
 *
 * <p>다만 Mongo 인기방 정렬은 DB 레벨 `sort(msgCnt)`(`idx_category_msgCnt`)로 후보를 뽑으므로 이 계산기를
 * 호출하지 않는다. 산식이 저장 필드 `msgCnt`와 크게 갈라지면 후보 선정(Mongo)과 최종 스코어(계산기)가
 * 어긋날 수 있어, 그때는 Mongo에 popularity 필드를 두는 등 후보 선정도 함께 손봐야 한다.
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
