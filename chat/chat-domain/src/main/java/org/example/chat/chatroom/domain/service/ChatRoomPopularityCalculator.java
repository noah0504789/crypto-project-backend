package org.example.chat.chatroom.domain.service;

import org.example.chat.chatroom.domain.model.ChatRoom;

/**
 * 채팅방 인기도 점수 산식의 단일 정의처. 현재는 {@code msgCnt} 단일 항이다.
 *
 * <p>실시간 증분이 아니라 주기 재계산으로 Redis zset 과 Mongo {@code popularity} 필드를 채운다.
 * 항을 추가하면 재계산 경로가 함께 움직인다 — 배경은 {@code docs/modules/CHAT.md} §12.
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
