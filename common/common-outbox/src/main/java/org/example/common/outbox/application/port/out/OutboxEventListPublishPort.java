package org.example.common.outbox.application.port.out;

import org.example.common.outbox.domain.event.AbstractOutboxEventList;

public interface OutboxEventListPublishPort {

    void publish(AbstractOutboxEventList eventList);
}