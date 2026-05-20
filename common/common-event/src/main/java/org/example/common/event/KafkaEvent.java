package org.example.common.event;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.example.common.enums.KafkaHeaderKey;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

public interface KafkaEvent {

    @JsonIgnore
    String getPartitionKey();

    default Message<KafkaEvent> toMessage() {
        return MessageBuilder
                .withPayload(this)
                .setHeader(KafkaHeaders.KEY, getPartitionKey())
                .setHeader(KafkaHeaderKey.TYPE_ID.value(), this.getClass().getName())
                .build();
    }
}
