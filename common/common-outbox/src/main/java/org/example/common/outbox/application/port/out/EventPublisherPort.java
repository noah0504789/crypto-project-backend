package org.example.common.outbox.application.port.out;

import org.example.common.dlq.domain.Dlq;
import org.example.common.outbox.domain.Outbox;

public interface EventPublisherPort {

    void publish(Outbox outbox);

    void publish(Dlq dlq);
}
