package org.example.common.dlq.application.port.out;

import org.example.common.dlq.domain.event.AbstractDlqEventList;

public interface DlqEventListPublishPort {

    void publish(AbstractDlqEventList eventList);
}