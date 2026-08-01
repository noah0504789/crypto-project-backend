package org.example.common.outbox.domain.event;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.example.common.util.EventIdUtils;
import org.example.common.outbox.adapter.out.JpaOutbox;
import org.example.common.outbox.domain.OutboxDispatchType;
import org.example.common.outbox.domain.OutboxDomainType;
import org.example.common.outbox.domain.OutboxStatus;

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

    protected abstract OutboxDomainType getDomainType();

    public JpaOutbox toOutbox(String transactionId, String payload) {
        return JpaOutbox.builder()
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
        return EventIdUtils.generateUlid();
    }
}
