package org.example.outboxpoller.infra.event;

import lombok.RequiredArgsConstructor;
import org.example.common.outbox.application.port.out.EventPublisherPort;
import org.example.common.dlq.adapter.out.JpaDlq;
import org.example.common.event.KafkaEventFactory;
import org.example.common.outbox.adapter.out.JpaOutbox;
import org.example.outboxpoller.infra.exception.OutboxPollerInfrastructureException;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KafkaEventPublisher implements EventPublisherPort {

    private final StreamBridge streamBridge;

    public void publish(JpaOutbox outbox) {
        Message<String> message = KafkaEventFactory.createOutboxEventMessage(
                outbox.getPayload(),
                outbox.getPartitionKey(),
                outbox.getEventType(),
                outbox.getId(),
                outbox.getTransactionId()
        );

        send(outbox.getDestination(), message, "outbox");
    }

    public void publish(JpaDlq dlq) {
        Message<String> message = KafkaEventFactory.createDlqEventMessage(
                dlq.getPayload(),
                dlq.getAggregateId(),
                dlq.getEventType(),
                dlq.getId(),
                dlq.getTransactionId(),
                dlq.getId()
        );

        send(dlq.getDestination(), message, "dlq");
    }

    private void send(String destination, Message<String> message, String source) {
        boolean sent = streamBridge.send(destination, message);

        if (!sent) {
            throw new OutboxPollerInfrastructureException(source + " publish failed! destination=" + destination);
        }
    }
}
