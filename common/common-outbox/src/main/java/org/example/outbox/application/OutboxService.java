package org.example.outbox.application;

import org.example.outbox.domain.Outbox;

import java.util.List;

public interface OutboxService {

    void save(Outbox outbox);

    void saveAll(List<Outbox> outboxes);
}
