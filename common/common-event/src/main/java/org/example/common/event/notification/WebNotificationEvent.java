package org.example.common.event.notification;

import org.example.common.enums.KafkaTopic;
import org.example.common.event.KafkaEvent;
import org.example.common.event.ProducibleEvent;
import org.example.common.event.TypedPayload;

import static org.example.common.enums.KafkaTopic.NOTIFICATION_WEB;

public record WebNotificationEvent(
        String eventType,
        WebNotificationPayload payload,
        String partitionKey
) implements KafkaEvent, ProducibleEvent {

    public WebNotificationEvent(String eventType, TypedPayload payload, String partitionKey) {
        this(
                eventType,
                WebNotificationPayload.fromTypedPayload(payload),
                partitionKey
        );
    }

    @Override
    public String getPartitionKey() {
        if (partitionKey == null || partitionKey.isBlank()) {
            throw new IllegalStateException("Notification partition key is missing.");
        }

        return partitionKey;
    }

    @Override
    public KafkaTopic getTopic() {
        return NOTIFICATION_WEB;
    }
}