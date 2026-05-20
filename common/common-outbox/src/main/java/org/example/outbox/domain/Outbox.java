package org.example.outbox.domain;

import jakarta.persistence.*;
import lombok.*;
import org.example.common.data.jpa.BaseEntity;

@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Outbox extends BaseEntity {

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

    public static Outbox ofPending(
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
        return Outbox.builder()
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
