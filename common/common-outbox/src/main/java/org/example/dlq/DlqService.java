package org.example.dlq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dlq.adapter.DlqRepository;
import org.example.outbox.application.EventPublisherPort;
import org.example.dlq.Dlq;
import org.example.dlq.DlqStatus;
import org.example.outbox.properties.DlqPollerProperties;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DlqService {

    private final EventPublisherPort eventPublisher;
    private final DlqRepository dlqRepository;
    private final DlqPollerProperties dlqPollerProperties;

    @Transactional("transactionManager")
    public void publishPending() {
        List<Dlq> pendings = dlqRepository.findByStatusOrderByCreatedAtAsc(DlqStatus.PENDING, PageRequest.of(0, dlqPollerProperties.batchSize()));

        for (Dlq dlq : pendings) {
            try {
                eventPublisher.publish(dlq);
                dlq.markPublished();

                log.info("✅ [dlq] publish success: dlqId={}, txId={}, eventType={}",
                        dlq.getId(),
                        dlq.getTransactionId(),
                        dlq.getEventType()
                );
            } catch (Exception e) {
                dlq.markPublishFailed();
                log.error("❌ [dlq] publish failed: dlqId={}, txId={}, eventType={}, error={}",
                        dlq.getId(),
                        dlq.getTransactionId(),
                        dlq.getEventType(),
                        e.getMessage(),
                        e
                );
            }
        }
    }
}
