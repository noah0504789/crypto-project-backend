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

    protected OutboxDispatchType getDispatchType() {
        return OutboxDispatchType.GENERAL;
    }

    protected String getMessageType() {
        return this.getClass().getName();
    }

    protected abstract OutboxDomainType getDomainType();

    // adapter(out) 매핑용 공개 접근자. 실제 값 계산은 protected 훅에 위임해 하위 override를 그대로 존중한다.
    public OutboxDispatchType dispatchType() {
        return getDispatchType();
    }

    public String messageType() {
        return getMessageType();
    }

    public OutboxDomainType domainType() {
        return getDomainType();
    }

    public String generateId() {
        return EventIdUtils.generateUlid();
    }
}
