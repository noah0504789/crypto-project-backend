package org.example.common.outbox.application.port.out;

import org.example.common.dlq.adapter.out.JpaDlq;
import org.example.common.outbox.adapter.out.JpaOutbox;

public interface EventPublisherPort {

    void publish(JpaOutbox outbox);

    void publish(JpaDlq dlq);
}
