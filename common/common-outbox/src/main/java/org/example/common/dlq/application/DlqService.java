package org.example.common.dlq.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.common.exception.DlqNotFoundException;
import org.example.common.dlq.domain.DlqStatus;
import org.example.common.dlq.adapter.DlqRepository;
import org.example.common.dlq.domain.Dlq;
import org.example.common.outbox.application.port.out.EventPublisherPort;
import org.example.common.dlq.properties.DlqPollerProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DlqService {

    private final ObjectProvider<EventPublisherPort> eventPublisherProvider;
    private final DlqRepository dlqRepository;
    private final ObjectProvider<DlqPollerProperties> dlqPollerPropertiesProvider;

    @Transactional("transactionManager")
    public void save(Dlq dlq) {
        dlqRepository.save(dlq);
    }

    @Transactional("transactionManager")
    public void saveAll(List<Dlq> dlqList) {
        dlqRepository.saveAll(dlqList);
    }

    @Transactional("transactionManager")
    public void complete(String id) {
        Dlq dlq = dlqRepository.findById(id)
                .orElseThrow(() -> new DlqNotFoundException(id));

        dlq.markCompleted();
    }

    @Transactional("transactionManager")
    public void fail(String id, String errorMessage) {
        Dlq dlq = dlqRepository.findById(id)
                .orElseThrow(() -> new DlqNotFoundException(id));

        dlq.markFailed(errorMessage);
    }

    @Transactional("transactionManager")
    public void publishPending() {
        EventPublisherPort publisher = eventPublisherProvider.getObject();
        int batchSize = dlqPollerPropertiesProvider.getObject().batchSize();
        List<Dlq> pendings = dlqRepository.findByStatusOrderByCreatedAtAsc(DlqStatus.PENDING, PageRequest.of(0, batchSize));

        for (Dlq dlq : pendings) {
            try {
                publisher.publish(dlq);
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
