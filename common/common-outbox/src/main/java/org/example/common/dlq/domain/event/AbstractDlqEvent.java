package org.example.common.dlq.domain.event;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.example.common.util.EventIdUtils;
import org.example.common.dlq.domain.DlqStatus;
import org.example.common.dlq.domain.Dlq;
import org.example.common.outbox.domain.OutboxDomainType;

@Getter
@RequiredArgsConstructor
public abstract class AbstractDlqEvent {

    @JsonIgnore
    private final String sourceId;

    @JsonIgnore
    private final String aggregateId;

    @JsonIgnore
    private final String aggregateType;

    @JsonIgnore
    private final OutboxDomainType domainType;

    @JsonIgnore
    private final String errorMessage;

    public Dlq toDlq(String transactionId, String payload) {
        return Dlq.builder()
                .id(generateId())
                .sourceId(this.sourceId)
                .eventType(this.getClass().getName())
                .aggregateId(this.aggregateId)
                .aggregateType(this.aggregateType)
                .transactionId(transactionId)
                .domainType(this.domainType)
                .status(DlqStatus.PENDING)
                .errorMessage(this.errorMessage)
                .payload(payload)
                .build();
    }

    private String generateId() {
        return EventIdUtils.generateId();
    }
}
