package org.example.common.event.notification;

import org.example.common.enums.KafkaTopic;
import org.example.common.event.KafkaEvent;
import org.example.common.event.ProducibleEvent;

import java.util.Map;

import static org.example.common.enums.KafkaTopic.NOTIFICATION_WEB;

public record WebNotificationEvent(
        String eventType,
        WebNotificationPayload payload,
        String partitionKey,
        String partitionKeyField
) implements KafkaEvent, ProducibleEvent {

    public WebNotificationEvent(String eventType, WebNotificationPayload payload, String partitionKey) {
        this(eventType, payload, partitionKey, null);
    }

    public WebNotificationEvent(String eventType, Map<String, Object> payload, String partitionKeyField) {
        this(eventType, WebNotificationPayload.fromData(payload), null, partitionKeyField);
    }

    @Override
    public String getPartitionKey() {
        if (partitionKey != null && !partitionKey.isBlank()) {
            return partitionKey;
        }

        if (partitionKeyField == null || partitionKeyField.isBlank()) {
            throw new IllegalStateException("Notification partition key is missing.");
        }

        Object payloadPartitionKey = payload.data().get(partitionKeyField);

        if (payloadPartitionKey == null) {
            throw new IllegalStateException(
                    "Notification partition key field is missing. field=" + partitionKeyField
            );
        }

        return payloadPartitionKey.toString();
    }

    @Override
    public KafkaTopic getTopic() {
        return NOTIFICATION_WEB;
    }
}
