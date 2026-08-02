package org.example.common.outbox.adapter.out;

import jakarta.persistence.*;
import lombok.*;
import org.example.common.jpa.BaseEntity;
import org.example.common.outbox.domain.OutboxDispatchType;
import org.example.common.outbox.domain.OutboxDomainType;
import org.example.common.outbox.domain.OutboxStatus;
import org.example.common.outbox.domain.event.AbstractOutboxEvent;

@Entity
@Table(name = "outbox")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JpaOutbox extends BaseEntity {

    @Id
    private String id;

    private String transactionId;
    private String aggregateId;
    private String aggregateType;
    private String partitionKey;

    @Lob
    @Column(nullable = false)
    private String payload;

    private String eventType;

    @Enumerated(EnumType.STRING)
    private OutboxDomainType domainType;

    @Enumerated(EnumType.STRING)
    private OutboxDispatchType dispatchType;

    @Enumerated(EnumType.STRING)
    private OutboxStatus status;

    private Integer retryCnt;

    public static JpaOutbox ofPending(
            String id,
            String transactionId,
            String aggregateId,
            String aggregateType,
            String partitionKey,
            String payload,
            String eventType,
            OutboxDomainType domainType,
            OutboxDispatchType dispatchType
    ) {
        return JpaOutbox.builder()
                .id(id)
                .transactionId(transactionId)
                .aggregateId(aggregateId)
                .aggregateType(aggregateType)
                .partitionKey(partitionKey)
                .payload(payload)
                .eventType(eventType)
                .domainType(domainType)
                .dispatchType(dispatchType)
                .status(OutboxStatus.PENDING)
                .retryCnt(0)
                .build();
    }

    public static JpaOutbox from(AbstractOutboxEvent event, String transactionId, String payload) {
        return JpaOutbox.builder()
                .id(event.generateId())
                .transactionId(transactionId)
                .aggregateId(event.getAggregateId())
                .aggregateType(event.getAggregateType())
                .partitionKey(event.getPartitionKey())
                .payload(payload)
                .eventType(event.messageType())
                .domainType(event.domainType())
                .dispatchType(event.dispatchType())
                .status(OutboxStatus.PENDING)
                .retryCnt(0)
                .build();
    }

    public String getDestination() {
        return aggregateType;
    }

    public void markPublished() {
        this.status = OutboxStatus.PUBLISHED;
    }

    public void markFailed() {
        this.status = OutboxStatus.FAILED;
    }

    public void increaseRetryCnt() {
        if (this.retryCnt == null) {
            this.retryCnt = 0;
        }

        this.retryCnt++;
    }

    public boolean isRetryExhausted(int maxRetryCnt) {
        if (this.retryCnt == null) {
            return false;
        }

        return this.retryCnt >= maxRetryCnt;
    }
}
