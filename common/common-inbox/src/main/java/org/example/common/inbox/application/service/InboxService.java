package org.example.common.inbox.application.service;

import lombok.RequiredArgsConstructor;
import org.example.common.inbox.adapter.out.InboxRepository;
import org.example.common.inbox.domain.Inbox;
import org.example.common.inbox.exception.DuplicateInboxException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InboxService {

    private final InboxRepository repository;

    @Transactional("transactionManager")
    public void save(String consumerName, String eventId) {
        try {
            repository.saveAndFlush(Inbox.of(consumerName, eventId));
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateInboxException(consumerName, eventId, e);
        }
    }
}
