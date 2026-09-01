package org.example.websocket.gateway.chatroom.adapter.out.stomp;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.example.websocket.gateway.chatroom.application.port.out.MyChatRoomBadgePort;
import org.example.websocket.gateway.chatroom.application.service.command.MyChatRoomBadgeCommand;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 뱃지를 방 단위 시간창으로 합쳐 brokerChannel 태스크 수를 줄인다. 뱃지는 내용이 아니라
 * 상태라 구간의 마지막 1건만 남기고 버린다 — 성립 근거와 대가는
 * {@code docs/modules/WEBSOCKET_GATEWAY.md} §6, 도입 근거는 PR #263.
 */
@Slf4j
@Primary
@Component
public class CoalescingMyChatRoomBadgeAdapter implements MyChatRoomBadgePort {

    private final StompMyChatRoomBadgeAdapter delegate;
    private final BadgeCoalesceProperties properties;

    private final Map<String, Pending> pending = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "badge-coalesce-");
                thread.setDaemon(true);
                return thread;
            });

    private final Counter coalesced;
    private final Counter flushed;
    private final Timer flushDuration;

    public CoalescingMyChatRoomBadgeAdapter(
            StompMyChatRoomBadgeAdapter delegate,
            BadgeCoalesceProperties properties,
            MeterRegistry registry
    ) {
        this.delegate = delegate;
        this.properties = properties;

        this.coalesced = Counter.builder("chat.badge.coalesced")
                .description("같은 방의 앞선 뱃지를 덮어써서 전송하지 않은 건수")
                .register(registry);
        this.flushed = Counter.builder("chat.badge.flushed")
                .description("합치기 창이 닫혀 실제로 전송한 뱃지 건수")
                .register(registry);
        this.flushDuration = Timer.builder("chat.badge.flush")
                .description("뱃지 합치기 버퍼를 한 사이클 비우는 데 걸린 시간")
                .register(registry);

        Gauge.builder("chat.badge.pending", pending, Map::size)
                .description("전송 대기 중인 방 수")
                .register(registry);
    }

    /**
     * 즉시 반환한다. 반환값은 "전송했다"가 아니라 "접수했다"는 뜻이다 —
     * 실제 수신자 유무는 창이 닫힌 뒤 {@link StompMyChatRoomBadgeAdapter} 가 판정한다.
     */
    @Override
    public boolean send(MyChatRoomBadgeCommand command, String txId) {
        if (!properties.enabled()) {
            return delegate.send(command, txId);
        }

        pending.merge(command.roomId(), new Pending(command, txId), this::keepLatest);

        return true;
    }

    // Kafka 키가 roomId 라 같은 방은 같은 파티션·같은 스레드로 순서대로 들어온다.
    // 그래도 타임스탬프로 한 번 더 거른다 — 파티션 재할당 중 순서가 흔들려도 과거 뱃지가
    // 최신을 덮지 않게 한다.
    private Pending keepLatest(Pending oldValue, Pending newValue) {
        coalesced.increment();

        Instant oldAt = oldValue.command().lastMsgCreatedAt();
        Instant newAt = newValue.command().lastMsgCreatedAt();

        if (oldAt == null || newAt == null) {
            return newValue;
        }

        return newAt.isBefore(oldAt) ? oldValue : newValue;
    }

    @PostConstruct
    public void start() {
        if (!properties.enabled()) {
            log.info("[badge] coalescing disabled. 멤버별 즉시 전송으로 동작한다");
            return;
        }

        long windowMs = properties.windowMs();

        // fixedRate 가 아니라 fixedDelay 다. brokerChannel 이 CallerRunsPolicy 라 flush 가 창보다
        // 오래 걸릴 수 있는데, fixedRate 면 밀린 실행이 연달아 터진다.
        scheduler.scheduleWithFixedDelay(this::flush, windowMs, windowMs, TimeUnit.MILLISECONDS);

        log.info("[badge] coalescing enabled. windowMs={}", windowMs);
    }

    // 스케줄러가 주기적으로 부르고 테스트는 직접 부른다. 여러 번 불러도 안전하다.
    public void flush() {
        flushDuration.record(this::drainPending);
    }

    private void drainPending() {
        try {
            for (String roomId : pending.keySet()) {
                Pending target = pending.remove(roomId);

                if (target == null) {
                    continue;
                }

                delegate.send(target.command(), target.txId());
                flushed.increment();
            }
        } catch (Exception e) {
            // 여기서 예외가 새면 스케줄러가 멈추고 뱃지가 영영 안 나간다.
            log.error("[badge] coalesce flush failed", e);
        }
    }

    @PreDestroy
    public void stop() {
        scheduler.shutdown();

        // 남은 버퍼를 한 번 비우고 내려간다. 종료 중 유실을 줄이려는 최선 노력이며 보장은 아니다.
        flush();
    }

    private record Pending(MyChatRoomBadgeCommand command, String txId) {
    }
}
