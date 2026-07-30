package org.example.common.inbox.domain.event;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import org.example.common.enums.KafkaHeaderKey;
import org.example.common.util.EventIdUtils;
import org.springframework.messaging.Message;

import java.nio.charset.StandardCharsets;

@Getter
public abstract class AbstractInboxEvent {

    @JsonIgnore
    private final String eventId;

    protected AbstractInboxEvent() {
        this.eventId = EventIdUtils.generateUUID();
    }

    public final String extractEventId(Message<?> message) {
        Object header = message.getHeaders().get(KafkaHeaderKey.EVENT_ID.value());

        if (header instanceof byte[] bytes) {
            String value = new String(bytes, StandardCharsets.UTF_8);
            if (!value.isBlank()) {
                return value;
            }
        }
        if (header instanceof String value && !value.isBlank()) {
            return value;
        }

        throw new IllegalArgumentException("event_id header is missing");
    }
}
