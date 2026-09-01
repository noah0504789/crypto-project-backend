package org.example.chat.chatmessage.adapter.out.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.example.chat.chatmessage.application.enums.ChatMessageMetricNames;
import org.example.chat.chatmessage.application.enums.ChatMessageInsertStage;
import org.example.chat.chatmessage.application.port.out.ChatMessageMetricsPort;
import org.example.common.tx.AfterCommitExecutor;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

@Component
public class MicrometerChatMessageMetricsAdapter implements ChatMessageMetricsPort {

    private final MeterRegistry registry;
    private final Timer handlerSuccessTimer;
    private final Timer handlerFailureTimer;
    private final Map<ChatMessageInsertStage, Timer> stageTimers;
    private final DistributionSummary batchMessageSummary;
    private final DistributionSummary batchRoomSummary;
    private final Counter newMessageCounter;
    private final Counter duplicateMessageCounter;
    private final Counter retryableFailureCounter;
    private final Counter dlqPublishedCounter;
    private final Counter dlqPublishFailedCounter;

    public MicrometerChatMessageMetricsAdapter(MeterRegistry registry) {
        this.registry = registry;
        this.handlerSuccessTimer = handlerTimer(registry, "success");
        this.handlerFailureTimer = handlerTimer(registry, "failure");
        this.stageTimers = new EnumMap<>(ChatMessageInsertStage.class);
        for (ChatMessageInsertStage stage : ChatMessageInsertStage.values()) {
            stageTimers.put(stage, stageTimer(registry, stage));
        }

        this.batchMessageSummary = summary(
                registry,
                ChatMessageMetricNames.BATCH_MESSAGES,
                "messages",
                "Number of chat messages in a committed persistence batch"
        );
        this.batchRoomSummary = summary(
                registry,
                ChatMessageMetricNames.BATCH_ROOMS,
                "rooms",
                "Number of distinct rooms in a committed persistence batch"
        );
        this.newMessageCounter = messageCounter(registry, "new");
        this.duplicateMessageCounter = messageCounter(registry, "duplicate");
        this.retryableFailureCounter = Counter.builder(ChatMessageMetricNames.RETRY_FAILURES)
                .description("Retryable chat message persistence attempt failures")
                .register(registry);
        this.dlqPublishedCounter = dlqCounter(registry, "published");
        this.dlqPublishFailedCounter = dlqCounter(registry, "publish_failed");
    }

    @Override
    public void recordHandler(Runnable action) {
        Timer.Sample sample = Timer.start(registry);
        try {
            action.run();
            sample.stop(handlerSuccessTimer);
        } catch (RuntimeException | Error e) {
            sample.stop(handlerFailureTimer);
            throw e;
        }
    }

    @Override
    public void recordMessageInsert(Runnable action) {
        recordStage(ChatMessageInsertStage.MESSAGE_INSERT, action);
    }

    @Override
    public void recordRoomCounter(Runnable action) {
        recordStage(ChatMessageInsertStage.ROOM_COUNTER, action);
    }

    @Override
    public void recordCommittedBatch(int messageCount, int roomCount) {
        AfterCommitExecutor.run(() -> {
            batchMessageSummary.record(messageCount);
            batchRoomSummary.record(roomCount);
            newMessageCounter.increment(messageCount);
        });
    }

    @Override
    public void recordDuplicateMessage() {
        AfterCommitExecutor.run(duplicateMessageCounter::increment);
    }

    @Override
    public void recordRetryableFailure() {
        retryableFailureCounter.increment();
    }

    @Override
    public void recordDlqPublished() {
        dlqPublishedCounter.increment();
    }

    @Override
    public void recordDlqPublishFailed() {
        dlqPublishFailedCounter.increment();
    }

    private static Timer handlerTimer(MeterRegistry registry, String result) {
        return Timer.builder(ChatMessageMetricNames.HANDLER_DURATION)
                .description("End-to-end chat message persistence consumer duration")
                .tag("result", result)
                .publishPercentileHistogram()
                .register(registry);
    }

    private static Timer stageTimer(MeterRegistry registry, ChatMessageInsertStage stage) {
        return Timer.builder(ChatMessageMetricNames.STAGE_DURATION)
                .description("Chat message Mongo persistence stage duration")
                .tag("stage", stage.getStageTagValue())
                .publishPercentileHistogram()
                .register(registry);
    }

    private static DistributionSummary summary(
            MeterRegistry registry,
            String name,
            String baseUnit,
            String description
    ) {
        return DistributionSummary.builder(name)
                .baseUnit(baseUnit)
                .description(description)
                .register(registry);
    }

    private static Counter messageCounter(MeterRegistry registry, String result) {
        return Counter.builder(ChatMessageMetricNames.MESSAGES)
                .description("Committed new and duplicate chat messages")
                .tag("result", result)
                .register(registry);
    }

    private static Counter dlqCounter(MeterRegistry registry, String result) {
        return Counter.builder(ChatMessageMetricNames.DLQ_TRANSITIONS)
                .description("Chat message persistence DLQ publish transitions")
                .tag("result", result)
                .register(registry);
    }

    private void recordStage(ChatMessageInsertStage stage, Runnable action) {
        stageTimers.get(stage).record(action);
    }

}
