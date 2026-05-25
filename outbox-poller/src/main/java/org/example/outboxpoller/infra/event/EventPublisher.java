package org.example.outboxpoller.infra.event;

import lombok.RequiredArgsConstructor;
import org.example.common.outbox.application.EventPublisherPort;
import org.example.common.enums.KafkaHeaderKey;
import org.example.common.dlq.domain.Dlq;
import org.example.common.outbox.domain.Outbox;
import org.example.outboxpoller.infra.exception.OutboxPollerInfrastructureException;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EventPublisher implements EventPublisherPort {

    private final StreamBridge streamBridge;

    public void publish(Outbox outbox) {
        Message<String> message = MessageBuilder.withPayload(outbox.getPayload())
                .setHeader(KafkaHeaders.KEY, outbox.getPartitionKey())
                .setHeader(KafkaHeaderKey.TRANSACTION_ID.value(), outbox.getTransactionId())
                .setHeader(KafkaHeaderKey.TYPE_ID.value(), outbox.getEventType())
                .build();

        send(outbox.getDestination(), message, "outbox");
    }

    public void publish(Dlq dlq) {
        Message<String> message = MessageBuilder.withPayload(dlq.getPayload())
                .setHeader(KafkaHeaders.KEY, dlq.getAggregateId())
                .setHeader(KafkaHeaderKey.DLQ_ID.value(), dlq.getId())
                .setHeader(KafkaHeaderKey.TRANSACTION_ID.value(), dlq.getTransactionId())
                .setHeader(KafkaHeaderKey.TYPE_ID.value(), dlq.getEventType())
                .build();

        send(dlq.getDestination(), message, "dlq");
    }

    private void send(String destination, Message<String> message, String source) {
        boolean sent = streamBridge.send(destination, message);

        if (!sent) {
            throw new OutboxPollerInfrastructureException(source + " publish failed! destination=" + destination);
        }
    }
}
