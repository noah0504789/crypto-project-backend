package org.example.common.dlq.domain;

import jakarta.persistence.*;
import lombok.*;
import org.example.common.jpa.BaseEntity;
import org.example.common.outbox.domain.OutboxDomainType;

@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Dlq extends BaseEntity {

    @Id
    private String id;

    private String sourceId;
    private String eventType;
    private String aggregateId;
    private String aggregateType;
    private String transactionId;

    @Enumerated(EnumType.STRING)
    private OutboxDomainType domainType;

    @Enumerated(EnumType.STRING)
    private DlqStatus status;
    private String errorMessage;

    @Lob
    @Column(nullable = false)
    private String payload;

    public static Dlq ofPending(
            String id,
            String sourceId,
            String eventType,
            String aggregateId,
            String aggregateType,
            String transactionId,
            OutboxDomainType domainType,
            String errorMessage,
            String payload
    ) {
        return Dlq.builder()
                .id(id)
                .sourceId(sourceId)
                .eventType(eventType)
                .aggregateId(aggregateId)
                .aggregateType(aggregateType)
                .transactionId(transactionId)
                .domainType(domainType)
                .status(DlqStatus.PENDING)
                .errorMessage(errorMessage)
                .payload(payload)
                .build();
    }

    public String getDestination() {
        return aggregateType;
    }

    public void markPublished() {
        this.status = DlqStatus.PUBLISHED;
    }

    public void markPublishFailed() {
        this.status = DlqStatus.PUBLISH_FAILED;
    }

    public void markCompleted() {
        this.status = DlqStatus.COMPLETED;
    }

    public void markFailed(String errorMessage) {
        this.status = DlqStatus.COMSUME_FAILED;
        this.errorMessage = errorMessage;
    }
}
