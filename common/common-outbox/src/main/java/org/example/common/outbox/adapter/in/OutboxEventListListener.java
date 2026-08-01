package org.example.common.outbox.adapter.in;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.common.outbox.adapter.out.OutboxPersistenceExceptionTranslator;
import org.example.common.outbox.application.service.OutboxService;
import org.example.common.outbox.adapter.out.JpaOutbox;
import org.example.common.outbox.domain.event.AbstractOutboxEvent;
import org.example.common.outbox.domain.event.AbstractOutboxEventList;
import org.example.common.outbox.exception.OutboxPersistenceException;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventListListener {

    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    @EventListener
    public void handleOutboxEventList(AbstractOutboxEventList eventList) {
        String txId = eventList.getTxId();

        try {
            List<JpaOutbox> outboxes = new ArrayList<>();

            for (AbstractOutboxEvent event : eventList.getEventList()) {
                String payload = objectMapper.writeValueAsString(event);
                outboxes.add(event.toOutbox(txId, payload));
            }

            outboxService.saveAll(outboxes);
        } catch (JsonProcessingException e) {
            log.error(
                    "[outbox] serialization failed. txId={}, error={}",
                    txId,
                    e.getMessage(),
                    e
            );

            throw new OutboxPersistenceException(
                    "failed to serialize outbox events. txId=" + txId,
                    e
            );
        } catch (DataAccessException e) {
            log.error(
                    "[outbox] save failed. txId={}, error={}",
                    txId,
                    e.getMessage(),
                    e
            );

            throw OutboxPersistenceExceptionTranslator.translate(
                    "failed to save outbox events. txId=" + txId,
                    e
            );
        } catch (Exception e) {
            log.error(
                    "[outbox] unexpected failure. txId={}, error={}",
                    txId,
                    e.getMessage(),
                    e
            );

            throw OutboxPersistenceExceptionTranslator.translate(
                    "failed to handle outbox event list. txId=" + txId,
                    e
            );
        }
    }
}