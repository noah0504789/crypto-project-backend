package org.example.common.inbox.application.service;

import lombok.RequiredArgsConstructor;
import org.example.common.inbox.adapter.out.InboxRepository;
import org.example.common.inbox.domain.Inbox;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InboxService {

    private final InboxRepository repository;

    @Transactional("transactionManager")
    public void save(String consumerName, String eventId) {
        repository.insertAndFlush(Inbox.of(consumerName, eventId));
    }
}
