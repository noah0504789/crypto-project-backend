package org.example.chat.chatroom.application.service.result;

/**
 * projector 가 dirty 목록에서 원자적으로 가져온 방 하나. {@code activityMs} 는 claim 시점까지
 * 합쳐진 최신 활동 시각이며, flush 는 이 값과 현재 캐시 상태 중 최신을 쓴다.
 */
public record ChatRoomActivityClaim(String roomId, long activityMs) {
}
