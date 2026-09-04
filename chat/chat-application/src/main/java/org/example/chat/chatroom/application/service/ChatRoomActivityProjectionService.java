package org.example.chat.chatroom.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.chat.chatroom.application.port.in.ChatRoomActivityProjectionUseCase;
import org.example.chat.chatroom.application.port.out.ChatRoomActivityProjectionMetricsPort;
import org.example.chat.chatroom.application.port.out.ChatRoomActivityProjectionPort;
import org.example.chat.chatroom.application.port.out.ChatRoomPersistencePort;
import org.example.chat.chatroom.application.properties.ChatRoomActivityProjectionProperties;
import org.example.chat.chatroom.application.service.result.ChatRoomActivityClaim;
import org.example.chat.chatroom.application.service.result.ChatRoomActivityProjectionResult;
import org.example.chat.chatroom.application.service.result.ChatRoomMemberActivity;
import org.example.chat.chatroom.application.service.result.ChatRoomMemberReadState;
import org.example.chat.chatroom.domain.model.ChatRoom;
import org.example.chat.chatroom.domain.service.MyChatRoomScoreCalculator;
import org.example.common.time.Clock;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 메시지마다 방 멤버 전원의 정렬 점수를 갱신하지 않고, dirty 로 표시된 방을 주기적으로 한 번씩만
 * 반영한다. 같은 방에 메시지 100건이 몰려도 멤버 갱신은 flush 한 번으로 끝나므로 쓰기량이
 * 메시지 수가 아니라 flush 한 방 수에 비례한다.
 *
 * <p>사실 기준은 Mongo 다 — 방 watermark {@code latestMsgSeq} 와 membership 의 {@code lastMsgReadSeq}.
 * Redis {@code last_read} hash 는 그 값을 실시간으로 읽기 위한 projector 입력이고, active-room ZSET 은
 * 조회를 빠르게 하려고 두는 재생성 가능한 결과다. Redis 가 비면 Mongo 에서 다시 만든다.
 *
 * <p>멤버별 정렬 score는 이 projector가 방 단위로 계산해 active-room ZSET에 반영한다. 메시지 저장은
 * 작성자의 읽음 위치와 방 dirty 표시만 남기며, projector가 계산한 score와 기존 active-room 값의
 * 차이는 {@code score.mismatches}로 관측한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRoomActivityProjectionService implements ChatRoomActivityProjectionUseCase {

    private final ChatRoomActivityProjectionPort projection;
    private final ChatRoomPersistencePort persistence;
    private final ChatRoomActivityProjectionProperties properties;
    private final ChatRoomActivityProjectionMetricsPort metrics;
    private final Clock clock;

    @Override
    public void flush() {
        metrics.recordFlush(this::drainClaimedRooms);
    }

    /**
     * claim 한 뒤 처리하지 못하고 죽은 방을 회수한다. Redis 경로가 실패했던 방이므로
     * 다시 Redis 를 믿지 않고 Mongo 기준으로 재생성한다.
     */
    @Override
    public void reclaimStalled() {
        long nowMs = clock.nowMs();
        long staleBeforeMs = nowMs - properties.claimTimeoutMs();

        List<String> rooms = projection.reclaimStalledRooms(staleBeforeMs, properties.reclaimBatchSize(), nowMs);

        if (rooms.isEmpty()) {
            return;
        }

        log.warn("[projection] reclaiming stalled chatroom activity. rooms={}", rooms.size());
        metrics.recordReclaimedRooms(rooms.size());

        rooms.forEach(this::rebuildSafely);
    }

    private void drainClaimedRooms() {
        long nowMs = clock.nowMs();
        List<ChatRoomActivityClaim> claims = projection.claimDirtyRooms(properties.claimBatchSize(), nowMs);

        metrics.recordDirtyBacklog(projection.countDirtyRooms());

        if (claims.isEmpty()) {
            return;
        }

        metrics.recordClaimedRooms(claims.size());
        claims.forEach(this::projectClaim);
    }

    private void projectClaim(ChatRoomActivityClaim claim) {
        try {
            ChatRoomActivityProjectionResult result = projection.project(claim.roomId(), claim.activityMs());

            if (result.cacheMiss()) {
                rebuild(claim.roomId());
                return;
            }

            metrics.recordProjectedRoom(result.updatedMembers(), result.mismatchedMembers());
        } catch (RuntimeException e) {
            metrics.recordFailedRoom();
            log.warn(
                    "[projection] chatroom activity projection failed. roomId={}, activityMs={}",
                    claim.roomId(),
                    claim.activityMs(),
                    e
            );

            requeueSafely(claim);
        }
    }

    private void rebuildSafely(String roomId) {
        try {
            rebuild(roomId);
        } catch (RuntimeException e) {
            metrics.recordFailedRoom();
            log.warn("[projection] chatroom activity rebuild failed. roomId={}", roomId, e);
        }
    }

    private void rebuild(String roomId) {
        Optional<ChatRoom> room = persistence.findByIdWithLatestMessage(roomId);

        if (room.isEmpty()) {
            projection.discard(roomId);
            metrics.recordDiscardedRoom();
            return;
        }

        List<ChatRoomMemberActivity> memberActivities = toMemberActivities(room.get());

        projection.rebuild(roomId, memberActivities);
        metrics.recordRebuiltRoom(memberActivities.size());
    }

    private List<ChatRoomMemberActivity> toMemberActivities(ChatRoom room) {
        List<ChatRoomMemberReadState> readStates = persistence.listMemberReadStates(room.getId());
        long lastMsgCreatedAtMs = room.lastMsgCreatedAtMs();

        return readStates.stream()
                .map(state -> new ChatRoomMemberActivity(
                        state.memberId(),
                        state.lastMsgReadSeq(),
                        score(room, state.lastMsgReadSeq(), lastMsgCreatedAtMs)
                ))
                .toList();
    }

    private long score(ChatRoom room, long lastMsgReadSeq, long lastMsgCreatedAtMs) {
        return room.hasUnread(lastMsgReadSeq)
                ? MyChatRoomScoreCalculator.unread(lastMsgCreatedAtMs)
                : MyChatRoomScoreCalculator.read(lastMsgCreatedAtMs);
    }

    private void requeueSafely(ChatRoomActivityClaim claim) {
        try {
            projection.requeueDirty(claim.roomId(), claim.activityMs());
        } catch (RuntimeException e) {
            // 되돌리기까지 실패하면 방은 inflight 에 남는다. claim timeout 뒤 reclaim 이 Mongo 기준으로 회수한다.
            log.warn("[projection] chatroom activity requeue failed. roomId={}", claim.roomId(), e);
        }
    }
}
