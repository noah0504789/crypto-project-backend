package org.example.websocket.gateway.chatroom.adapter.out.stomp;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
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
 * 뱃지는 "내용"이 아니라 "상태"다. 방 하나에 100ms 사이 30건이 들어와도
 * 마지막 1건만 보내면 화면 결과가 같다.
 *
 * <p>{@link StompMyChatRoomBadgeAdapter} 는 멤버마다 {@code convertAndSendToUser} 를 호출하므로
 * brokerChannel 태스크가 메시지당 O(멤버수)다. 2026-08-28 측정(VU 80, 방 1개, 초당 80건) 기준
 * 뱃지가 broker 태스크의 98.8%(6,400/초)를 차지했고 broker 거절 75,694건의 주원인이었다.
 * 방 단위로 합치면 창 200ms 에서 80 라운드/초가 5 라운드/초가 된다.
 *
 * <p>합치기가 가능한 이유는 payload 에 개인별 값이 없기 때문이다 —
 * {@code StompMyChatRoomBadgePayload(roomId, lastMsgContent, lastMsgCreatedAt)}.
 * 개인별 필드가 생기면 이 최적화는 성립하지 않는다.
 *
 * <p>대가: Kafka 오프셋이 실제 전송보다 먼저 커밋된다. 게이트웨이가 죽으면 버퍼에 있던 뱃지는
 * 사라진다. 뱃지는 방 목록 재조회로 회복되므로 허용한다. 회복 불가능한 ACK 에는 쓰지 않는다.
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

        // scheduleAtFixedRate 가 아니라 scheduleWithFixedDelay 다. brokerChannel 이 CallerRunsPolicy 라
        // flush 스레드가 broker 태스크를 직접 실행하며 창보다 오래 걸릴 수 있는데, fixedRate 면
        // 밀린 실행이 연달아 터져 부하를 키운다. fixedDelay 는 느려진 만큼 창이 자연히 넓어져
        // 합치는 양이 늘고 스스로 배압이 된다.
        scheduler.scheduleWithFixedDelay(this::flush, windowMs, windowMs, TimeUnit.MILLISECONDS);

        log.info("[badge] coalescing enabled. windowMs={}", windowMs);
    }

    // 창을 한 번 닫고 대기 중인 방을 전부 내보낸다. 스케줄러가 주기적으로 부르고,
    // 테스트는 스케줄러 없이 직접 불러 결과를 확인한다. 여러 번 불러도 안전하다.
    public void flush() {
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
