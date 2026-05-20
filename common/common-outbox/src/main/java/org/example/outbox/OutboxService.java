package org.example.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.outbox.application.EventPublisherPort;
import org.example.outbox.adapter.OutboxRepository;
import org.example.outbox.properties.OutboxPollerProperties;
import org.example.outbox.domain.Outbox;
import org.example.outbox.domain.OutboxDispatchType;
import org.example.outbox.domain.OutboxStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxRepository outboxRepository;
    private final EventPublisherPort outboxPublisher;
    private final OutboxPollerProperties outboxPollerProperties;

    @Transactional("transactionManager")
    public void publishPending(OutboxDispatchType dispatchType) {
        OutboxPollerProperties.Item props = outboxPollerProperties.get(dispatchType);

        List<Outbox> pendings = outboxRepository.findByDispatchTypeAndStatusOrderByCreatedAtAsc(dispatchType, OutboxStatus.PENDING, PageRequest.of(0, props.batchSize()));

        for (Outbox outbox : pendings) {
            try {
                outboxPublisher.publish(outbox);
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
