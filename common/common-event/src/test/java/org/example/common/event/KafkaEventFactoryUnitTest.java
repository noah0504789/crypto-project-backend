package org.example.common.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaEventFactoryUnitTest {

    @Test
    @DisplayName("직접 발행 메시지는 payload와 필수 Kafka 헤더를 생성한다")
    void createEventMessage() {
        // given
        TestKafkaEvent payload = new TestKafkaEvent("aggregate-1");

        // when
        Message<TestKafkaEvent> message = KafkaEventFactory.createEventMessage(
                payload,
                payload.getPartitionKey(),
                payload.getClass().getName()
        );

        // then
        assertThat(message.getPayload()).isSameAs(payload);
        assertThat(message.getHeaders().get(KafkaHeaders.KEY)).isEqualTo("aggregate-1");
        assertThat(message.getHeaders().get("__TypeId__")).isEqualTo(TestKafkaEvent.class.getName());
        assertThat(message.getHeaders()).doesNotContainKeys("transaction_id", "dlq_id");
    }

    @Test
    @DisplayName("Outbox 메시지는 transaction_id 헤더를 함께 생성한다")
    void createOutboxMessage() {
        // when
        Message<String> message = KafkaEventFactory.createOutboxEventMessage(
                "{\"message\":\"hello\"}",
                "room-1",
                "ChatMessageCreatedEvent",
                "event-1",
                "tx-1"
        );

        // then
        assertThat(message.getPayload()).isEqualTo("{\"message\":\"hello\"}");
        assertThat(message.getHeaders().get(KafkaHeaders.KEY)).isEqualTo("room-1");
        assertThat(message.getHeaders().get("__TypeId__")).isEqualTo("ChatMessageCreatedEvent");
        assertThat(message.getHeaders().get("event_id")).isEqualTo("event-1");
        assertThat(message.getHeaders().get("transaction_id")).isEqualTo("tx-1");
        assertThat(message.getHeaders()).doesNotContainKey("dlq_id");
    }

    @Test
    @DisplayName("DLQ 메시지는 transaction_id와 dlq_id 헤더를 함께 생성한다")
    void createDlqMessage() {
        // when
        Message<String> message = KafkaEventFactory.createDlqEventMessage(
                "{\"failed\":\"event\"}",
                "aggregate-1",
                "ChatMessageFailedEvent",
                "event-2",
                "tx-2",
                "dlq-1"
        );

        // then
        assertThat(message.getPayload()).isEqualTo("{\"failed\":\"event\"}");
        assertThat(message.getHeaders().get(KafkaHeaders.KEY)).isEqualTo("aggregate-1");
        assertThat(message.getHeaders().get("__TypeId__")).isEqualTo("ChatMessageFailedEvent");
        assertThat(message.getHeaders().get("event_id")).isEqualTo("event-2");
        assertThat(message.getHeaders().get("transaction_id")).isEqualTo("tx-2");
        assertThat(message.getHeaders().get("dlq_id")).isEqualTo("dlq-1");
    }

    private record TestKafkaEvent(String partitionKey) implements KafkaEvent {

        @Override
        public String getPartitionKey() {
            return partitionKey;
        }
    }
}
