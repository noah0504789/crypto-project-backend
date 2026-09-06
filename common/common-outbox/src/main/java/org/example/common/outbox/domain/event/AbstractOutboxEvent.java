package org.example.common.outbox.domain.event;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.example.common.util.EventIdUtils;
import org.example.common.outbox.domain.OutboxDispatchType;
import org.example.common.outbox.domain.OutboxDomainType;

@Getter
@RequiredArgsConstructor
public abstract class AbstractOutboxEvent {

    @JsonIgnore
    private final String aggregateType;

    @JsonIgnore
    private final String aggregateId;

    @JsonIgnore
    private final String partitionKey;

    @JsonIgnore
    public OutboxDispatchType getDispatchType() {
        return OutboxDispatchType.GENERAL;
    }

    @JsonIgnore
    public String getMessageType() {
        return this.getClass().getName();
    }

    @JsonIgnore
    public abstract OutboxDomainType getDomainType();

    public String generateId() {
        return EventIdUtils.generateUlid();
    }
}
