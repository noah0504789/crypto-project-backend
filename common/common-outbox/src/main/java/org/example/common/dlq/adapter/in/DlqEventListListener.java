package org.example.common.dlq.adapter.in;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.common.dlq.adapter.out.DlqPersistenceExceptionTranslator;
import org.example.common.dlq.domain.event.AbstractDlqEvent;
import org.example.common.dlq.domain.event.AbstractDlqEventList;
import org.example.common.dlq.adapter.out.JpaDlq;
import org.example.common.dlq.application.service.DlqService;
import org.example.common.dlq.exception.DlqPersistenceException;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DlqEventListListener {

    private final DlqService dlqService;
    private final ObjectMapper objectMapper;

    @EventListener
    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public void handleDlqEventList(AbstractDlqEventList eventList) {
        String txId = eventList.getTxId();

        try {
            List<JpaDlq> dlqList = new ArrayList<>();

            for (AbstractDlqEvent event : eventList.getEventList()) {
                String payload = objectMapper.writeValueAsString(event);
                dlqList.add(JpaDlq.from(event, txId, payload));
            }

            dlqService.saveAll(dlqList);
        } catch (JsonProcessingException e) {
            log.error(
                    "[dlq] serialization failed. txId={}, error={}",
                    txId,
                    e.getMessage(),
                    e
            );

            throw new DlqPersistenceException("failed to serialize dlq events. txId=" + txId, e);
        } catch (DataAccessException e) {
            log.error(
                    "[dlq] save failed. txId={}, error={}",
                    txId,
                    e.getMessage(),
                    e
            );

            throw DlqPersistenceExceptionTranslator.translate("failed to save dlq events. txId=" + txId, e);
        } catch (Exception e) {
            log.error(
                    "[dlq] unexpected failure. txId={}, error={}",
                    txId,
                    e.getMessage(),
                    e
            );

            throw DlqPersistenceExceptionTranslator.translate("failed to handle dlq event list. txId=" + txId, e);
        }
    }
}