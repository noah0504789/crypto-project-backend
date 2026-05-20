package org.example.outbox.application;

import lombok.RequiredArgsConstructor;
import org.example.outbox.domain.Outbox;
import org.example.outbox.adapter.OutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OutboxCommandService implements OutboxService {

    private final OutboxRepository outboxRepository;

    @Override
    @Transactional("transactionManager")
    public void save(Outbox outbox) {
        outboxRepository.save(outbox);
    }

    @Override
    public void saveAll(List<Outbox> outboxes) {
        outboxRepository.saveAll(outboxes);
    }
}
