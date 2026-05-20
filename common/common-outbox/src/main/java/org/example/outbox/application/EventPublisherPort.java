package org.example.outbox.application;

import org.example.dlq.Dlq;
import org.example.outbox.domain.Outbox;

public interface EventPublisherPort {
    void publish(Outbox outbox);

    void publish(Dlq dlq);
}
