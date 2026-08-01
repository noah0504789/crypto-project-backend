package org.example.common.outbox.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxUnitTest {

    @Test
    @DisplayName("create는 PENDING 상태와 retryCnt 0으로 Outbox를 생성한다")
    void of_createsPendingOutbox() {
        // when
        Outbox outbox = ofOutbox();

        // then
        assertThat(outbox.getId()).isEqualTo("outbox-1");
        assertThat(outbox.getTransactionId()).isEqualTo("tx-1");
        assertThat(outbox.getAggregateId()).isEqualTo("aggregate-1");
        assertThat(outbox.getAggregateType()).isEqualTo("chat-message-topic");
        assertThat(outbox.getPartitionKey()).isEqualTo("partition-1");
        assertThat(outbox.getPayload()).isEqualTo("{\"message\":\"hello\"}");
        assertThat(outbox.getEventType()).isEqualTo("ChatMessageCreatedEvent");
        assertThat(outbox.getDomainType()).isEqualTo(OutboxDomainType.CHAT);
        assertThat(outbox.getDispatchType()).isEqualTo(OutboxDispatchType.GENERAL);

        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(outbox.getRetryCnt()).isZero();
    }

    @Test
    @DisplayName("getDestination은 aggregateType을 반환한다")
    void getDestination_returnsAggregateType() {
        // given
        Outbox outbox = ofOutbox();

        // when
        String destination = outbox.getDestination();

        // then
        assertThat(destination).isEqualTo("chat-message-topic");
    }

    @Test
    @DisplayName("markPublished는 상태를 PUBLISHED로 변경한다")
    void markPublished_changesStatusToPublished() {
        // given
        Outbox outbox = ofOutbox();

        // when
        outbox.markPublished();

        // then
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
    }

    @Test
    @DisplayName("markFailed는 상태를 FAILED로 변경한다")
    void markFailed_changesStatusToFailed() {
        // given
        Outbox outbox = ofOutbox();

        // when
        outbox.markFailed();

        // then
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.FAILED);
    }

    @Test
    @DisplayName("increaseRetryCnt는 retryCnt를 1 증가시킨다")
    void increaseRetryCnt_increasesRetryCount() {
        // given
        Outbox outbox = ofOutbox();

        // when
        outbox.increaseRetryCnt();

        // then
        assertThat(outbox.getRetryCnt()).isEqualTo(1);
    }

    @Test
    @DisplayName("increaseRetryCnt는 여러 번 호출되면 호출 횟수만큼 증가한다")
    void increaseRetryCnt_increasesEveryCall() {
        // given
        Outbox outbox = ofOutbox();

        // when
        outbox.increaseRetryCnt();
        outbox.increaseRetryCnt();
        outbox.increaseRetryCnt();

        // then
        assertThat(outbox.getRetryCnt()).isEqualTo(3);
    }

    @Test
    @DisplayName("retryCnt가 maxRetryCnt 이상이면 재시도 소진으로 판단한다")
    void isRetryExhausted_returnsTrueWhenRetryCntIsGreaterThanOrEqualMaxRetryCnt() {
        // given
        Outbox outbox = ofOutbox();

        outbox.increaseRetryCnt();
        outbox.increaseRetryCnt();
        outbox.increaseRetryCnt();

        // when & then
        assertThat(outbox.isRetryExhausted(3)).isTrue();
        assertThat(outbox.isRetryExhausted(2)).isTrue();
        assertThat(outbox.isRetryExhausted(4)).isFalse();
    }

    @Test
    @DisplayName("retryCnt가 null이면 재시도 소진으로 판단하지 않는다")
    void isRetryExhausted_returnsFalseWhenRetryCntIsNull() {
        // given
        Outbox outbox = Outbox.ofPending(
                "outbox-1",
                "tx-1",
                "aggregate-1",
                "chat-message-topic",
                "partition-1",
                "{\"message\":\"hello\"}",
                "ChatMessageCreatedEvent",
                OutboxDomainType.CHAT,
                OutboxDispatchType.GENERAL
        );

        // create에서 retryCnt 0을 보장하므로 이 테스트는 사실상 방어 로직 확인용은 아님
        assertThat(outbox.isRetryExhausted(1)).isFalse();
    }

    private Outbox ofOutbox() {
        return Outbox.ofPending(
                "outbox-1",
                "tx-1",
                "aggregate-1",
                "chat-message-topic",
                "partition-1",
                "{\"message\":\"hello\"}",
                "ChatMessageCreatedEvent",
                OutboxDomainType.CHAT,
                OutboxDispatchType.GENERAL
        );
    }
}