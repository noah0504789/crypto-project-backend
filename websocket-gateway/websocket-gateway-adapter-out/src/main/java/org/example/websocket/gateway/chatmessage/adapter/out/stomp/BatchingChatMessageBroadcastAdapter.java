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
 * 수치와 실험 이력은 {@code TODO.md} 5.3.
 *
 * <p>뱃지 conflation 과 달리 <b>한 건도 버리지 않고 순서를 지킨다.</b> 그래서 버퍼가 방 수가 아니라
 * 유입량만큼 커지며, 방당 상한을 넘으면 버리는 대신 창이 닫히기 전에 내보낸다.
 *
 * <p>Kafka 오프셋이 실제 전송보다 먼저 커밋된다. 게이트웨이가 죽으면 버퍼가 유실되고
 * 클라이언트가 그 갭을 감지할 수단은 아직 없다(TODO 5.5).
 */
@Slf4j
@Primary
@Component
public class BatchingChatMessageBroadcastAdapter implements ChatMessageBroadcastPort {

    private final StompChatMessageBroadcastAdapter delegate;
    private final ChatMessageBatchProperties properties;

    private final Map<String, RoomBuffer> pending = new ConcurrentHashMap<>();

    // 뱃지 conflation 과 공유하지 않는다. brokerChannel 의 CallerRunsPolicy 에 걸려 늘어지면 서로 멈춘다.
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
     * 반환값은 "전송했다"가 아니라 "접수했다"는 뜻이다.
     * 로컬 멤버 판정은 여기서만 할 수 있다 — 창이 닫힌 뒤에는 {@code memberIds} 가 없다.
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

    // 적재는 compute 안에서만, 배출은 remove 로 통째로. 같은 키를 맵이 직렬화해주므로 버퍼에 락이 없다.
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

        // fixedRate 로 바꾸지 않는다. flush 가 CallerRunsPolicy 로 늘어지면 밀린 실행이 몰려 부하를 키운다.
        // fixedDelay 는 늦어진 만큼 창이 넓어져 스스로 배압이 된다.
        scheduler.scheduleWithFixedDelay(this::flush, windowMs, windowMs, TimeUnit.MILLISECONDS);

        log.info("[chat-batch] enabled. windowMs={}, maxBatchSize={}", windowMs, properties.maxBatchSize());
    }

    // 스케줄러와 테스트가 함께 쓰는 진입점. 여러 번 불러도 안전하다.
    public void flush() {
        try {
            for (String roomId : pending.keySet()) {
                flushRoom(roomId);
            }
        } catch (Exception e) {
            // 예외가 새면 스케줄러가 멈추고 메시지가 영영 안 나간다.
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

        // 최선 노력이며 보장은 아니다.
        flush();
    }

    // Kafka 키가 roomId 라 같은 방은 순서대로 들어온다.
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
