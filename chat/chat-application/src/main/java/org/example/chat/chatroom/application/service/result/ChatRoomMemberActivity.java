package org.example.chat.chatroom.application.service.result;

/**
 * 재생성 시 Redis 에 다시 심을 멤버별 상태. score 는 도메인 규칙
 * ({@code MyChatRoomScoreCalculator})으로 application 에서 계산해 넘긴다.
 */
public record ChatRoomMemberActivity(
        String memberId,
        long lastMsgReadSeq,
        long score
) {
}
