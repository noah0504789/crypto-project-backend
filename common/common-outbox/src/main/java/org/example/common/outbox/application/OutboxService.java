package org.example.common.outbox.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.common.outbox.adapter.OutboxRepository;
import org.example.common.outbox.properties.OutboxPollerProperties;
import org.example.common.outbox.domain.Outbox;
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

    private final OutboxRepository outboxRepository;
    private final ObjectProvider<EventPublisherPort> outboxPublisherProvider;
    private final ObjectProvider<OutboxPollerProperties> outboxPollerPropertiesProvider;

    @Transactional("transactionManager")
    public void save(Outbox outbox) {
        outboxRepository.save(outbox);
    }

    @Transactional("transactionManager")
    public void saveAll(List<Outbox> outboxes) {
        outboxRepository.saveAll(outboxes);
    }

    @Transactional("transactionManager")
    public void publishPending(OutboxDispatchType dispatchType) {
        EventPublisherPort publisher = outboxPublisherProvider.getObject();
        OutboxPollerProperties.Item props = outboxPollerPropertiesProvider.getObject().get(dispatchType);

        List<Outbox> pendings = outboxRepository.findByDispatchTypeAndStatusOrderByCreatedAtAsc(dispatchType, OutboxStatus.PENDING, PageRequest.of(0, props.batchSize()));

        for (Outbox outbox : pendings) {
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
