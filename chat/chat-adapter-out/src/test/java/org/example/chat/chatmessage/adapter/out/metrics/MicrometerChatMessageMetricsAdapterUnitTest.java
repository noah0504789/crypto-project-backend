package org.example.chat.chatmessage.adapter.out.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.example.chat.chatmessage.application.enums.ChatMessageMetricNames;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
class MicrometerChatMessageMetricsAdapterUnitTest {

    private MeterRegistry registry;
    private MicrometerChatMessageMetricsAdapter sut;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        sut = new MicrometerChatMessageMetricsAdapter(registry);
    }

    @Nested
    @DisplayName("handler 계측")
    class HandlerMetricsTest {

        @Test
        @DisplayName("핸들러가 정상 종료되면 success 타이머를 기록한다")
        void recordHandler_shouldRecordSuccess_whenActionCompletes() {
            // when
            sut.recordHandler(() -> { });

            // then
            assertThat(timerCount(ChatMessageMetricNames.HANDLER_DURATION, "result", "success")).isEqualTo(1L);
            assertThat(timerCount(ChatMessageMetricNames.HANDLER_DURATION, "result", "failure")).isZero();
        }

        @Test
        @DisplayName("핸들러가 실패하면 failure 타이머를 기록하고 예외를 전파한다")
        void recordHandler_shouldRecordFailureAndRethrow_whenActionFails() {
            // given
            RuntimeException exception = new RuntimeException("persistence failed");

            // when & then
            assertThatThrownBy(() -> sut.recordHandler(() -> {
                throw exception;
            })).isSameAs(exception);

            assertThat(timerCount(ChatMessageMetricNames.HANDLER_DURATION, "result", "failure")).isEqualTo(1L);
            assertThat(timerCount(ChatMessageMetricNames.HANDLER_DURATION, "result", "success")).isZero();
        }
    }

    @Test
    @DisplayName("Mongo 단계별 타이머를 stage 태그로 분리한다")
    void recordIndividualStages_shouldRecordTimerByStage() {
        // when
        sut.recordMessageInsert(() -> { });
        sut.recordRoomCounter(() -> { });
        sut.recordMembership(() -> { });

        // then
        assertThat(timerCount(ChatMessageMetricNames.STAGE_DURATION, "stage", "message_insert")).isEqualTo(1L);
        assertThat(timerCount(ChatMessageMetricNames.STAGE_DURATION, "stage", "room_counter")).isEqualTo(1L);
        assertThat(timerCount(ChatMessageMetricNames.STAGE_DURATION, "stage", "membership")).isEqualTo(1L);
    }

    @Test
    @DisplayName("신규 메시지와 쓰기량은 트랜잭션 커밋 뒤에만 기록한다")
    void recordCommittedBatch_shouldRecordOnlyAfterCommit() {
        // given
        TransactionSynchronizationManager.initSynchronization();

        try {
            // when
            sut.recordCommittedBatch(3, 2, 10);

            // then
            assertThat(counterCount(ChatMessageMetricNames.MESSAGES, "result", "new")).isZero();
            assertThat(summaryTotal(ChatMessageMetricNames.BATCH_MESSAGES)).isZero();

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);

            assertThat(counterCount(ChatMessageMetricNames.MESSAGES, "result", "new")).isEqualTo(3.0);
            assertThat(summaryTotal(ChatMessageMetricNames.BATCH_MESSAGES)).isEqualTo(3.0);
            assertThat(summaryTotal(ChatMessageMetricNames.BATCH_ROOMS)).isEqualTo(2.0);
            assertThat(summaryTotal(ChatMessageMetricNames.MEMBERSHIP_DOCUMENTS)).isEqualTo(10.0);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("중복 메시지는 duplicate 카운터로 분리한다")
    void recordDuplicateMessage_shouldIncrementDuplicateCounter() {
        // when
        sut.recordDuplicateMessage();

        // then
        assertThat(counterCount(ChatMessageMetricNames.MESSAGES, "result", "duplicate")).isEqualTo(1.0);
        assertThat(counterCount(ChatMessageMetricNames.MESSAGES, "result", "new")).isZero();
    }

    @Test
    @DisplayName("재시도 가능한 영속 실패 횟수를 기록한다")
    void recordRetryableFailure_shouldIncrementCounter() {
        // when
        sut.recordRetryableFailure();

        // then
        assertThat(registry.get(ChatMessageMetricNames.RETRY_FAILURES).counter().count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("DLQ 발행 성공과 실패 전이를 분리한다")
    void recordDlqTransition_shouldSeparatePublishedAndFailed() {
        // when
        sut.recordDlqPublished();
        sut.recordDlqPublishFailed();

        // then
        assertThat(counterCount(ChatMessageMetricNames.DLQ_TRANSITIONS, "result", "published")).isEqualTo(1.0);
        assertThat(counterCount(ChatMessageMetricNames.DLQ_TRANSITIONS, "result", "publish_failed")).isEqualTo(1.0);
    }

    private long timerCount(String name, String tagKey, String tagValue) {
        return registry.get(name).tag(tagKey, tagValue).timer().count();
    }

    private double counterCount(String name, String tagKey, String tagValue) {
        return registry.get(name).tag(tagKey, tagValue).counter().count();
    }

    private double summaryTotal(String name) {
        return registry.get(name).summary().totalAmount();
    }
}
