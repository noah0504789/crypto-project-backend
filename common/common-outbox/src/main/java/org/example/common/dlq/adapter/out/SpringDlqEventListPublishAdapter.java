package org.example.common.dlq.adapter.out;

import lombok.extern.slf4j.Slf4j;
import org.example.common.dlq.application.port.out.DlqEventListPublishPort;
import org.example.common.dlq.domain.event.AbstractDlqEventList;
import org.example.common.dlq.exception.DlqPersistenceException;
import org.example.common.event.EventUtils;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SpringDlqEventListPublishAdapter implements DlqEventListPublishPort {

    @Override
    public void publish(AbstractDlqEventList eventList) {
        if (eventList == null) {
            log.warn("[dlq] event list publish skipped. eventList is null");
            return;
        }

        if (eventList.getEventList().isEmpty()) {
            log.debug("[dlq] event list publish skipped. eventList is empty");
            return;
        }

        try {
            EventUtils.raise(eventList);
        } catch (DlqPersistenceException e) {
            throw e;
        } catch (Exception e) {
            throw DlqPersistenceExceptionTranslator.translate("failed to publish dlq event list. txId=" + eventList.getTxId(), e);
        }
    }
}