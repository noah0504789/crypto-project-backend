package org.example.common.outbox.adapter.out;

import lombok.extern.slf4j.Slf4j;
import org.example.common.event.EventUtils;
import org.example.common.outbox.application.port.out.OutboxEventListPublishPort;
import org.example.common.outbox.domain.event.AbstractOutboxEventList;
import org.example.common.outbox.exception.OutboxPersistenceException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SpringOutboxEventListPublishAdapter implements OutboxEventListPublishPort {

    @Override
    public void publish(AbstractOutboxEventList eventList) {
        if (eventList == null) {
            log.warn("[outbox] event list publish skipped. eventList is null");
            return;
        }

        if (eventList.getEventList().isEmpty()) {
            log.debug("[outbox] event list publish skipped. eventList is empty");
            return;
        }

        try {
            eventList.assignTxId();

            EventUtils.raise(eventList);
        } catch (OutboxPersistenceException e) {
            throw e;
        } catch (Exception e) {
            throw OutboxPersistenceExceptionTranslator.translate(
                    "failed to publish outbox event list. txId=" + eventList.getTxId(),
                    e
            );
        }
    }
}