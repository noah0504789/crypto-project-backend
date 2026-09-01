package org.example.chat.chatroom.application.service.result;

/**
 * 방 하나의 projection 결과. {@code cacheMiss} 는 방 캐시가 비어 Redis 만으로는 계산할 수 없다는
 * 뜻이고, 이때 호출자는 Mongo(durable source) 기준 재생성으로 넘긴다.
 *
 * <p>{@code mismatchedMembers} 는 projector 가 계산한 score 가 기존 fan-out 이 써 둔 값과 다른
 * 멤버 수다. 두 경로를 함께 돌리는 동안 결과를 대조하는 용도이며, 읽은 멤버를 read score 로
 * 되돌리는 정상 차이도 포함한다.
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
