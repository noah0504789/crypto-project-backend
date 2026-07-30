package org.example.common.event;

import org.example.common.enums.KafkaHeaderKey;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

public final class KafkaEventFactory {

    private KafkaEventFactory() {
    }

    public static <T> Message<T> createEventMessage(
            T payload,
            String partitionKey,
            String eventType
    ) {
        return createMessage(payload, partitionKey, eventType, null, null, null);
    }

    public static <T> Message<T> createOutboxEventMessage(
            T payload,
            String partitionKey,
            String eventType,
            String eventId,
            String transactionId
    ) {
        return createMessage(payload, partitionKey, eventType, eventId, transactionId, null);
    }

    public static <T> Message<T> createDlqEventMessage(
            T payload,
            String partitionKey,
            String eventType,
            String eventId,
            String transactionId,
            String dlqId
    ) {
        return createMessage(payload, partitionKey, eventType, eventId, transactionId, dlqId);
    }

    private static <T> Message<T> createMessage(
            T payload,
            String partitionKey,
            String eventType,
            String eventId,
            String transactionId,
            String dlqId
    ) {
        MessageBuilder<T> builder = MessageBuilder
                .withPayload(payload)
                .setHeader(KafkaHeaders.KEY, partitionKey)
                .setHeader(KafkaHeaderKey.TYPE_ID.value(), eventType);

        setHeaderIfPresent(builder, KafkaHeaderKey.EVENT_ID, eventId);
        setHeaderIfPresent(builder, KafkaHeaderKey.TRANSACTION_ID, transactionId);
        setHeaderIfPresent(builder, KafkaHeaderKey.DLQ_ID, dlqId);

        return builder.build();
    }

    private static <T> void setHeaderIfPresent(
            MessageBuilder<T> builder,
            KafkaHeaderKey headerKey,
            String value
    ) {
        if (value == null || value.isBlank()) {
            return;
        }

        builder.setHeader(headerKey.value(), value);
    }
}
