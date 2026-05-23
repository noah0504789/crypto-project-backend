package org.example.dlq.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dlq.domain.event.AbstractDlqEvent;
import org.example.dlq.domain.event.AbstractDlqEventList;
import org.example.dlq.domain.Dlq;
import org.example.dlq.application.DlqService;
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
    public void handleDlqEventList(AbstractDlqEventList eventList) throws JsonProcessingException {
        String txId = eventList.getTxId();

        try {
            List<Dlq> dlqList = new ArrayList<>();

            for (AbstractDlqEvent event : eventList.getEventList()) {
                String payload = objectMapper.writeValueAsString(event);
                dlqList.add(event.toDlq(txId, payload));
            }

            dlqService.saveAll(dlqList);
        } catch (JsonProcessingException e) {
            log.error("[Dlq 직렬화 실패], txId={}, error={}", txId, e.getMessage());
            throw e;
        } catch (DataAccessException e) {
            log.error("[Dlq 저장 실패], txId={}, error={}", txId, e.getMessage());
            throw e;
        }
    }
}
