package org.example.chat.chatroom.application.port.out;

import org.example.chat.chatroom.application.service.result.ChatRoomActivityClaim;
import org.example.chat.chatroom.application.service.result.ChatRoomActivityProjectionResult;
import org.example.chat.chatroom.application.service.result.ChatRoomMemberActivity;

import java.util.List;

/**
 * 내 방 정렬 projection(Redis active-room ZSET)을 유지하는 아웃바운드 포트.
 *
 * <p>계층은 셋으로 나뉜다.
 * <ul>
 *   <li>durable source of truth: Mongo {@code chat_room.latestMsgSeq} + {@code chat_room_membership.lastMsgReadSeq}</li>
 *   <li>실시간 projector 입력: Redis {@code last_read} hash</li>
 *   <li>조회용 결과: Redis active-room ZSET — 언제든 위 둘로 재생성 가능한 projection 이다</li>
 * </ul>
 * dirty 인덱스는 내구성 큐가 아니라 "처리할 방을 모아 두는 coalescing 작업 목록"이다.
 * 유실될 수 있으므로 재생성 경로가 항상 함께 있어야 한다.
 */
public interface ChatRoomActivityProjectionPort {

    List<ChatRoomActivityClaim> claimDirtyRooms(int batchSize, long nowMs);

    ChatRoomActivityProjectionResult project(String roomId, long claimedActivityMs);

    List<String> reclaimStalledRooms(long staleBeforeMs, int batchSize, long nowMs);

    void rebuild(String roomId, List<ChatRoomMemberActivity> memberActivities);

    void requeueDirty(String roomId, long activityMs);

    void discard(String roomId);

    long countDirtyRooms();
}
