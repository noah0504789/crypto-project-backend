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
 * 설계 근거·수치·대가는 2026-08-28 부하테스트 문서 §7-1.
 */
@Slf4j
@Primary
@Component
public class BatchingChatMessageBroadcastAdapter implements ChatMessageBroadcastPort {

    private final StompChatMessageBroadcastAdapter delegate;
    private final ChatMessageBatchProperties properties;

    private final Map<String, RoomBuffer> pending = new ConcurrentHashMap<>();

    // 공유 금지. CallerRunsPolicy 로 한쪽이 늘어지면 서로 멈춘다.
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

    /** 반환값은 "접수했다"는 뜻이다. */
    @Override
    public boolean broadcast(ChatMessageBroadcastCommand command, String txId) {
        if (!properties.enabled()) {
            return delegate.broadcast(command, txId);
        }

        if (!delegate.hasLocalSubscriber(command.roomId())) {
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

    // 적재는 compute 안에서만, 배출은 remove 로 통째로. 맵이 키를 직렬화해주므로 버퍼에 락이 없다.
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

        // fixedRate 로 바꾸지 않는다. 밀린 실행이 몰려 부하를 키운다.
        scheduler.scheduleWithFixedDelay(this::flush, windowMs, windowMs, TimeUnit.MILLISECONDS);

        log.info("[chat-batch] enabled. windowMs={}, maxBatchSize={}", windowMs, properties.maxBatchSize());
    }

    public void flush() {
        try {
            for (String roomId : pending.keySet()) {
                flushRoom(roomId);
            }
        } catch (Exception e) {
            // 예외가 새면 스케줄러가 멈춘다.
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

        flush();
    }

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
