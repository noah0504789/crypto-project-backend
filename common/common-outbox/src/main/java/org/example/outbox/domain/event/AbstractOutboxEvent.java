package org.example.outbox.domain.event;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.example.common.util.EventIdUtils;
import org.example.outbox.domain.Outbox;
import org.example.outbox.domain.OutboxDispatchType;
import org.example.outbox.domain.OutboxDomainType;
import org.example.outbox.domain.OutboxStatus;

@Getter
@RequiredArgsConstructor
public abstract class AbstractOutboxEvent {

    @JsonIgnore
    private final String aggregateType;

    @JsonIgnore
    private final String aggregateId;

    @JsonIgnore
    private final String partitionKey;

    protected OutboxDispatchType getDispatchType() {
        return OutboxDispatchType.GENERAL;
    }

    protected String getMessageType() {
        return this.getClass().getName();
    }

    protected OutboxDomainType getDomainType() {
        return OutboxDomainType.CHAT;
    }

    public Outbox toOutbox(String transactionId, String payload) {
        return Outbox.builder()
                .id(generateId())
                .transactionId(transactionId)
                .aggregateId(aggregateId)
                .aggregateType(aggregateType)
                .partitionKey(partitionKey)
                .payload(payload)
                .eventType(getMessageType())
                .domainType(getDomainType())
                .dispatchType(getDispatchType())
                .status(OutboxStatus.PENDING)
                .retryCnt(0)
                .build();
    }

    public String generateId() {
        return EventIdUtils.generateId();
    }
}
