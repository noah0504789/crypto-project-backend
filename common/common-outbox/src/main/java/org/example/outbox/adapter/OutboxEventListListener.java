package org.example.outbox.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.outbox.application.OutboxService;
import org.example.outbox.domain.Outbox;
import org.example.outbox.domain.event.AbstractOutboxEvent;
import org.example.outbox.domain.event.AbstractOutboxEventList;
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
    public void handleOutboxEventList(AbstractOutboxEventList eventList) throws JsonProcessingException {
        String txId = eventList.getTxId();

        try {
            List<Outbox> outboxes = new ArrayList<>();

            for (AbstractOutboxEvent event : eventList.getEventList()) {
                String payload = objectMapper.writeValueAsString(event);
                outboxes.add(event.toOutbox(txId, payload));
            }

            outboxService.saveAll(outboxes);
        } catch (JsonProcessingException e) {
            log.error("[Outbox 직렬화 실패], txId={}, error={}", txId, e.getMessage());
            throw e;
        } catch (DataAccessException e) {
            log.error("[Outbox 저장 실패], txId={}, error={}", txId, e.getMessage());
            throw e;
        }
    }
}
