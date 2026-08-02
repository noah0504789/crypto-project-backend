package org.example.common.outbox.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.common.outbox.adapter.out.JpaOutboxRepository;
import org.example.common.outbox.application.port.out.EventPublisherPort;
import org.example.common.outbox.properties.OutboxPollerProperties;
import org.example.common.outbox.adapter.out.JpaOutbox;
import org.example.common.outbox.domain.OutboxDispatchType;
import org.example.common.outbox.domain.OutboxStatus;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final JpaOutboxRepository jpaOutboxRepository;
    private final ObjectProvider<EventPublisherPort> outboxPublisherProvider;
    private final ObjectProvider<OutboxPollerProperties> outboxPollerPropertiesProvider;

    @Transactional("transactionManager")
    public void save(JpaOutbox outbox) {
        jpaOutboxRepository.save(outbox);
    }

    @Transactional("transactionManager")
    public void saveAll(List<JpaOutbox> outboxes) {
        jpaOutboxRepository.saveAll(outboxes);
    }

    @Transactional("transactionManager")
    public void publishPending(OutboxDispatchType dispatchType) {
        EventPublisherPort publisher = outboxPublisherProvider.getObject();
        OutboxPollerProperties.Item props = outboxPollerPropertiesProvider.getObject().get(dispatchType);

        List<JpaOutbox> pendings = jpaOutboxRepository.findByDispatchTypeAndStatusOrderByCreatedAtAsc(dispatchType, OutboxStatus.PENDING, PageRequest.of(0, props.batchSize()));

        for (JpaOutbox outbox : pendings) {
            try {
                publisher.publish(outbox);
                outbox.markPublished();
            } catch (Exception e) {
                outbox.increaseRetryCnt();

                if (outbox.isRetryExhausted(props.maxRetryCnt())) {
                    outbox.markFailed();
                }

                log.error("[outbox] publish failed! id={}, txId={}, topic={}, retryCnt={}, error={}",
                        outbox.getId(),
                        outbox.getTransactionId(),
                        outbox.getAggregateType(),
                        outbox.getRetryCnt(),
                        e.getMessage()
                );
            }
        }
    }
}
