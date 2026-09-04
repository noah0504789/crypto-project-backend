package org.example.chat.chatroom.adapter.out.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.example.chat.chatroom.application.enums.ChatRoomActivityProjectionMetricNames;
import org.example.chat.chatroom.application.port.out.ChatRoomActivityProjectionMetricsPort;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 방 activity projector 계측. 태그는 고정된 저카디널리티 값만 쓰고 {@code roomId}·{@code memberId} 는
 * 태그로 넣지 않는다.
 *
 * <p>{@code score.mismatches} 는 projection 반영 전의 active-room score와 projector 계산이
 * 다른 멤버 수다. score가 실제로 변한 정상 갱신도 포함하므로 0이 목표값은 아니며, 변화량의
 * 추이를 보는 지표다.
 */
@Component
public class MicrometerChatRoomActivityProjectionMetricsAdapter implements ChatRoomActivityProjectionMetricsPort {

    private final Timer flushTimer;
    private final Counter claimedRoomCounter;
    private final Counter projectedRoomCounter;
    private final Counter rebuiltRoomCounter;
    private final Counter reclaimedRoomCounter;
    private final Counter discardedRoomCounter;
    private final Counter failedRoomCounter;
    private final DistributionSummary projectedMemberSummary;
    private final DistributionSummary rebuiltMemberSummary;
    private final Counter scoreMismatchCounter;
    private final AtomicLong dirtyBacklog = new AtomicLong();

    public MicrometerChatRoomActivityProjectionMetricsAdapter(MeterRegistry registry) {
        this.flushTimer = Timer.builder(ChatRoomActivityProjectionMetricNames.FLUSH_DURATION)
                .description("Duration of one chat room activity projection flush cycle")
                .register(registry);

        this.claimedRoomCounter = roomCounter(registry, "claimed");
        this.projectedRoomCounter = roomCounter(registry, "projected");
        this.rebuiltRoomCounter = roomCounter(registry, "rebuilt");
        this.reclaimedRoomCounter = roomCounter(registry, "reclaimed");
        this.discardedRoomCounter = roomCounter(registry, "discarded");
        this.failedRoomCounter = roomCounter(registry, "failed");

        this.projectedMemberSummary = memberSummary(registry, "projection");
        this.rebuiltMemberSummary = memberSummary(registry, "rebuild");

        this.scoreMismatchCounter = Counter.builder(ChatRoomActivityProjectionMetricNames.SCORE_MISMATCHES)
                .description("Members whose projected sort score differs from the previous active-room value")
                .register(registry);

        Gauge.builder(ChatRoomActivityProjectionMetricNames.DIRTY_BACKLOG, dirtyBacklog, AtomicLong::get)
                .description("Rooms waiting in the activity projection dirty index")
                .register(registry);
    }

    @Override
    public void recordFlush(Runnable action) {
        flushTimer.record(action);
    }

    @Override
    public void recordClaimedRooms(int rooms) {
        claimedRoomCounter.increment(rooms);
    }

    @Override
    public void recordProjectedRoom(int updatedMembers, int mismatchedMembers) {
        projectedRoomCounter.increment();
        projectedMemberSummary.record(updatedMembers);
        scoreMismatchCounter.increment(mismatchedMembers);
    }

    @Override
    public void recordRebuiltRoom(int members) {
        rebuiltRoomCounter.increment();
        rebuiltMemberSummary.record(members);
    }

    @Override
    public void recordReclaimedRooms(int rooms) {
        reclaimedRoomCounter.increment(rooms);
    }

    @Override
    public void recordDiscardedRoom() {
        discardedRoomCounter.increment();
    }

    @Override
    public void recordFailedRoom() {
        failedRoomCounter.increment();
    }

    @Override
    public void recordDirtyBacklog(long dirtyRooms) {
        dirtyBacklog.set(dirtyRooms);
    }

    private Counter roomCounter(MeterRegistry registry, String result) {
        return Counter.builder(ChatRoomActivityProjectionMetricNames.ROOMS)
                .description("Chat rooms handled by the activity projector")
                .tag("result", result)
                .register(registry);
    }

    private DistributionSummary memberSummary(MeterRegistry registry, String source) {
        return DistributionSummary.builder(ChatRoomActivityProjectionMetricNames.MEMBERS)
                .description("Members updated per room by the activity projector")
                .baseUnit("members")
                .tag("source", source)
                .register(registry);
    }
}
