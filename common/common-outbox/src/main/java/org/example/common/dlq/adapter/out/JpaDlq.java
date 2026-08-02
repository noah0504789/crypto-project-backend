package org.example.common.dlq.adapter.out;

import jakarta.persistence.*;
import lombok.*;
import org.example.common.dlq.domain.DlqStatus;
import org.example.common.dlq.domain.event.AbstractDlqEvent;
import org.example.common.jpa.BaseEntity;
import org.example.common.outbox.domain.OutboxDomainType;

@Entity
@Table(name = "dlq")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JpaDlq extends BaseEntity {

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

    public static JpaDlq ofPending(
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
        return JpaDlq.builder()
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

    public static JpaDlq from(AbstractDlqEvent event, String transactionId, String payload) {
        return JpaDlq.builder()
                .id(event.generateId())
                .sourceId(event.getSourceId())
                .eventType(event.getClass().getName())
                .aggregateId(event.getAggregateId())
                .aggregateType(event.getAggregateType())
                .transactionId(transactionId)
                .domainType(event.getDomainType())
                .status(DlqStatus.PENDING)
                .errorMessage(event.getErrorMessage())
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
        this.status = DlqStatus.CONSUME_FAILED;
        this.errorMessage = errorMessage;
    }
}
