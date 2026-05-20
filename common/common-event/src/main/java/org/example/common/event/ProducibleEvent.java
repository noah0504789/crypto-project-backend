package org.example.common.event;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.example.common.enums.KafkaTopic;

public interface ProducibleEvent {

    @JsonIgnore
    KafkaTopic getTopic();
}
