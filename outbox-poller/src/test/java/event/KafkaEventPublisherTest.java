package event;

import org.example.common.dlq.domain.Dlq;
import org.example.outboxpoller.infra.event.KafkaEventPublisher;
import org.example.outboxpoller.infra.exception.OutboxPollerInfrastructureException;
import org.example.common.outbox.domain.Outbox;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaEventPublisherTest {

    @Mock
    private StreamBridge streamBridge;

    @InjectMocks
    private KafkaEventPublisher sut;

    @Test
    @DisplayName("Outbox를 publish하면 destination으로 payload와 headers를 담은 메시지를 전송한다")
    void publishOutbox_sendsMessage() {
        // given
        Outbox outbox = mock(Outbox.class);

        when(outbox.getDestination()).thenReturn("chat-outbox-out-0");
        when(outbox.getPayload()).thenReturn("{\"message\":\"hello\"}");
        when(outbox.getPartitionKey()).thenReturn("room-1");
        when(outbox.getTransactionId()).thenReturn("tx-1");
        when(outbox.getEventType()).thenReturn("ChatMessageCreatedEvent");

        when(streamBridge.send(eq("chat-outbox-out-0"), any(Message.class)))
                .thenReturn(true);

        ArgumentCaptor<Message<String>> messageCaptor =
                ArgumentCaptor.forClass(Message.class);

        // when
        sut.publish(outbox);

        // then
        verify(streamBridge).send(eq("chat-outbox-out-0"), messageCaptor.capture());

        Message<String> message = messageCaptor.getValue();

        assertThat(message.getPayload()).isEqualTo("{\"message\":\"hello\"}");
        assertThat(message.getHeaders().get(KafkaHeaders.KEY)).isEqualTo("room-1");
        assertThat(message.getHeaders().get("transaction_id")).isEqualTo("tx-1");
        assertThat(message.getHeaders().get("__TypeId__")).isEqualTo("ChatMessageCreatedEvent");
    }

    @Test
    @DisplayName("Outbox publish 실패 시 예외가 발생한다")
    void publishOutbox_throwsExceptionWhenSendFails() {
        // given
        Outbox outbox = mock(Outbox.class);

        when(outbox.getDestination()).thenReturn("chat-outbox-out-0");
        when(outbox.getPayload()).thenReturn("{}");
        when(outbox.getPartitionKey()).thenReturn("room-1");
        when(outbox.getTransactionId()).thenReturn("tx-1");
        when(outbox.getEventType()).thenReturn("ChatMessageCreatedEvent");

        when(streamBridge.send(eq("chat-outbox-out-0"), any(Message.class)))
                .thenReturn(false);

        // when & then
        assertThatThrownBy(() -> sut.publish(outbox))
                .isInstanceOf(OutboxPollerInfrastructureException.class)
                .hasMessageContaining("outbox publish failed")
                .hasMessageContaining("destination=chat-outbox-out-0");

        verify(streamBridge).send(eq("chat-outbox-out-0"), any(Message.class));
    }

    @Test
    @DisplayName("Dlq를 publish하면 destination으로 payload와 headers를 담은 메시지를 전송한다")
    void publishDlq_sendsMessage() {
        // given
        Dlq dlq = mock(Dlq.class);

        when(dlq.getDestination()).thenReturn("chat-dlq-out-0");
        when(dlq.getPayload()).thenReturn("{\"failed\":\"event\"}");
        when(dlq.getAggregateId()).thenReturn("aggregate-1");
        when(dlq.getId()).thenReturn(String.valueOf(100));
        when(dlq.getTransactionId()).thenReturn("tx-2");
        when(dlq.getEventType()).thenReturn("ChatMessageFailedEvent");

        when(streamBridge.send(eq("chat-dlq-out-0"), any(Message.class)))
                .thenReturn(true);

        ArgumentCaptor<Message<String>> messageCaptor =
                ArgumentCaptor.forClass(Message.class);

        // when
        sut.publish(dlq);

        // then
        verify(streamBridge).send(eq("chat-dlq-out-0"), messageCaptor.capture());

        Message<String> message = messageCaptor.getValue();

        assertThat(message.getPayload()).isEqualTo("{\"failed\":\"event\"}");
        assertThat(message.getHeaders().get(KafkaHeaders.KEY)).isEqualTo("aggregate-1");
        assertThat(message.getHeaders().get("dlq_id")).isEqualTo(100+"");
        assertThat(message.getHeaders().get("transaction_id")).isEqualTo("tx-2");
        assertThat(message.getHeaders().get("__TypeId__")).isEqualTo("ChatMessageFailedEvent");
    }

    @Test
    @DisplayName("Dlq publish 실패 시 예외가 발생한다")
    void publishDlq_throwsExceptionWhenSendFails() {
        // given
        Dlq dlq = mock(Dlq.class);

        when(dlq.getDestination()).thenReturn("chat-dlq-out-0");
        when(dlq.getPayload()).thenReturn("{}");
        when(dlq.getAggregateId()).thenReturn("aggregate-1");
        when(dlq.getId()).thenReturn(String.valueOf(100));
        when(dlq.getTransactionId()).thenReturn("tx-2");
        when(dlq.getEventType()).thenReturn("ChatMessageFailedEvent");

        when(streamBridge.send(eq("chat-dlq-out-0"), any(Message.class)))
                .thenReturn(false);

        // when & then
        assertThatThrownBy(() -> sut.publish(dlq))
                .isInstanceOf(OutboxPollerInfrastructureException.class)
                .hasMessageContaining("dlq publish failed")
                .hasMessageContaining("destination=chat-dlq-out-0");

        verify(streamBridge).send(eq("chat-dlq-out-0"), any(Message.class));
    }
}
