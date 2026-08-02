package org.example.common.dlq.domain.event;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.example.common.util.EventIdUtils;
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

    public String generateId() {
        return EventIdUtils.generateUlid();
    }
}
