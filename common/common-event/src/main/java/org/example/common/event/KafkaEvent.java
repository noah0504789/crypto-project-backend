package org.example.common.event;

import com.fasterxml.jackson.annotation.JsonIgnore;

public interface KafkaEvent {

    @JsonIgnore
    String getPartitionKey();
}
