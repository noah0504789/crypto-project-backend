package org.example.websocket.gateway.chatmessage.adapter.out.stomp;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.example.websocket.gateway.chatmessage.adapter.out.stomp.payload.StompChatMessagePayload;
import org.example.websocket.gateway.chatmessage.application.port.out.ChatMessageBroadcastPort;
import org.example.websocket.gateway.chatmessage.application.service.command.ChatMessageBroadcastCommand;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 같은 방의 메시지를 시간창으로 묶어 brokerChannel 태스크 수를 줄인다.
 *
 * <p>메시지는 "내용"이라 <b>한 건도 버리지 않고 순서를 지킨다.</b> 뱃지 conflation 과 이 점이 다르다 —
 * 거기는 구간의 마지막 1건만 남기고 나머지를 버린다.
 *
 * <p>줄어드는 것은 프레임 수이지 전달되는 메시지 수가 아니다. 브로커의 구독자 확장(×N)은 그대로이며,
 * 프레임 하나가 여러 건을 담아 나간다.
 *
 * <pre>
 *                    배칭 전        배칭 후(100ms, 초당 80건, 구독자 80명)
 * brokerChannel      80 태스크/초   10 태스크/초
 * outbound 프레임    6,400/초       800/초
 * 전달 메시지        6,400/초       6,400/초   (같다)
 * </pre>
 *
 * <p>버퍼가 방 수가 아니라 <b>유입량만큼</b> 커지므로 방당 상한을 둔다. 상한을 넘으면 버리지 않고
 * 창이 닫히기 전에 즉시 내보낸다.
 *
 * <p>대가: Kafka 오프셋이 실제 전송보다 먼저 커밋된다. 게이트웨이가 죽으면 버퍼에 있던 메시지는
 * 이 인스턴스의 구독자에게 전달되지 않는다. 방 재진입 시 Mongo 조회로 회복되지만, 그 사이 갭을
 * 클라이언트가 감지할 수단이 없다(→ TODO 5.5).
 */
@Slf4j
@Primary
@Component
public class BatchingChatMessageBroadcastAdapter implements ChatMessageBroadcastPort {

    private final StompChatMessageBroadcastAdapter delegate;
    private final ChatMessageBatchProperties properties;

    private final Map<String, RoomBuffer> pending = new ConcurrentHashMap<>();

    // 뱃지 conflation 과 스케줄러를 공유하지 않는다. 한쪽 flush 가 brokerChannel 의
    // CallerRunsPolicy 에 걸려 늘어지면 다른 쪽까지 함께 멈춘다.
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "chat-batch-");
                thread.setDaemon(true);
                return thread;
            });

    private final Counter buffered;
    private final Counter frames;
    private final Counter overflowFlushes;
    private final DistributionSummary batchSize;

    public BatchingChatMessageBroadcastAdapter(
            StompChatMessageBroadcastAdapter delegate,
            ChatMessageBatchProperties properties,
            MeterRegistry registry
    ) {
        this.delegate = delegate;
        this.properties = properties;

        this.buffered = Counter.builder("chat.message.batch.buffered")
                .description("배칭 버퍼에 적재한 메시지 수")
                .register(registry);
        this.frames = Counter.builder("chat.message.batch.frames")
                .description("실제로 내보낸 프레임 수")
                .register(registry);
        this.overflowFlushes = Counter.builder("chat.message.batch.overflow")
                .description("방당 상한 초과로 창을 기다리지 않고 내보낸 횟수")
                .register(registry);
        this.batchSize = DistributionSummary.builder("chat.message.batch.size")
                .description("프레임 하나에 담긴 메시지 수")
                .register(registry);

        Gauge.builder("chat.message.batch.pending.rooms", pending, Map::size)
                .description("전송 대기 중인 방 수")
                .register(registry);
    }

    /**
     * 즉시 반환한다. 반환값은 "전송했다"가 아니라 "접수했다"는 뜻이다.
     * 로컬 멤버가 없으면 적재하지 않고 {@code false} 를 돌려준다 — 창이 닫힌 뒤에는
     * 멤버 정보가 없어 같은 판정을 다시 할 수 없으므로 거르는 위치는 여기여야 한다.
     */
    @Override
    public boolean broadcast(ChatMessageBroadcastCommand command, String txId) {
        if (!properties.enabled()) {
            return delegate.broadcast(command, txId);
        }

        if (!delegate.hasAnyLocalMember(command.memberIds())) {
            return false;
        }

        int size = append(command.roomId(), StompChatMessagePayload.from(command), txId);

        buffered.increment();

        if (size >= properties.maxBatchSize()) {
            overflowFlushes.increment();
            flushRoom(command.roomId());
        }

        return true;
    }

    // 적재는 compute 안에서만 일어나고 배출은 remove 로 통째로 가져간다.
    // 같은 키에 대해 ConcurrentHashMap 이 직렬화해주므로 버퍼 자체에 락이 필요 없고,
    // 배출한 뒤에는 그 버퍼를 flush 스레드가 단독으로 소유한다.
    private int append(String roomId, StompChatMessagePayload payload, String txId) {
        int[] sizeHolder = new int[1];

        pending.compute(roomId, (key, buffer) -> {
            RoomBuffer target = (buffer == null) ? new RoomBuffer(txId) : buffer;
            target.add(payload);
            sizeHolder[0] = target.size();

            return target;
        });

        return sizeHolder[0];
    }

    @PostConstruct
    public void start() {
        if (!properties.enabled()) {
            log.info("[chat-batch] disabled. 메시지마다 즉시 전송으로 동작한다");
            return;
        }

        long windowMs = properties.windowMs();

        // scheduleAtFixedRate 가 아니라 scheduleWithFixedDelay 다. brokerChannel 이 CallerRunsPolicy 라
        // flush 스레드가 broker 태스크를 직접 실행하며 창보다 오래 걸릴 수 있는데, fixedRate 면
        // 밀린 실행이 연달아 터져 부하를 키운다. fixedDelay 는 느려진 만큼 창이 넓어져
        // 한 프레임에 더 많이 담기고 스스로 배압이 된다.
        scheduler.scheduleWithFixedDelay(this::flush, windowMs, windowMs, TimeUnit.MILLISECONDS);

        log.info("[chat-batch] enabled. windowMs={}, maxBatchSize={}", windowMs, properties.maxBatchSize());
    }

    // 창을 한 번 닫고 대기 중인 방을 전부 내보낸다. 스케줄러가 주기적으로 부르고,
    // 테스트는 스케줄러 없이 직접 불러 결과를 확인한다. 여러 번 불러도 안전하다.
    public void flush() {
        try {
            for (String roomId : pending.keySet()) {
                flushRoom(roomId);
            }
        } catch (Exception e) {
            // 여기서 예외가 새면 스케줄러가 멈추고 메시지가 영영 안 나간다.
            log.error("[chat-batch] flush failed", e);
        }
    }

    private void flushRoom(String roomId) {
        RoomBuffer buffer = pending.remove(roomId);

        if (buffer == null || buffer.isEmpty()) {
            return;
        }

        List<StompChatMessagePayload> messages = buffer.drain();

        delegate.broadcastBatch(roomId, messages, buffer.txId());

        frames.increment();
        batchSize.record(messages.size());
    }

    @PreDestroy
    public void stop() {
        scheduler.shutdown();

        // 남은 버퍼를 한 번 비우고 내려간다. 종료 중 유실을 줄이려는 최선 노력이며 보장은 아니다.
        flush();
    }

    // 적재 순서를 그대로 유지한다. Kafka 키가 roomId 라 같은 방은 순서대로 들어온다.
    private static final class RoomBuffer {

        private final List<StompChatMessagePayload> messages = new ArrayList<>();
        private final String txId;

        private RoomBuffer(String txId) {
            this.txId = txId;
        }

        private void add(StompChatMessagePayload payload) {
            messages.add(payload);
        }

        private int size() {
            return messages.size();
        }

        private boolean isEmpty() {
            return messages.isEmpty();
        }

        private List<StompChatMessagePayload> drain() {
            return List.copyOf(messages);
        }

        private String txId() {
            return txId;
        }
    }
}
