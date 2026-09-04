package org.example.chat.chatroom.application.service.result;

/**
 * 방 하나의 projection 결과. {@code cacheMiss} 는 방 캐시가 비어 Redis 만으로는 계산할 수 없다는
 * 뜻이고, 이때 호출자는 Mongo(durable source) 기준 재생성으로 넘긴다.
 *
 * <p>{@code mismatchedMembers} 는 projector 가 계산한 score 가 기존 active-room projection 값과
 * 다른 멤버 수다. projection 갱신 전후의 차이를 관측하는 용도이며, score가 실제로 변한 정상
 * 갱신도 포함한다.
 */
public record ChatRoomActivityProjectionResult(
        int updatedMembers,
        int mismatchedMembers,
        boolean cacheMiss
) {

    private static final ChatRoomActivityProjectionResult CACHE_MISS =
            new ChatRoomActivityProjectionResult(0, 0, true);

    public static ChatRoomActivityProjectionResult ofCacheMiss() {
        return CACHE_MISS;
    }

    public static ChatRoomActivityProjectionResult of(int updatedMembers, int mismatchedMembers) {
        return new ChatRoomActivityProjectionResult(updatedMembers, mismatchedMembers, false);
    }
}
