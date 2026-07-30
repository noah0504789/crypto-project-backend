package org.example.common.inbox.application.service;

import lombok.RequiredArgsConstructor;
import org.example.common.inbox.adapter.out.InboxEventRepository;
import org.example.common.inbox.domain.InboxEvent;
import org.example.common.inbox.exception.DuplicateInboxEventException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InboxEventService {

    private final InboxEventRepository repository;

    @Transactional("transactionManager")
    public void save(String consumerName, String eventId) {
        try {
            repository.saveAndFlush(InboxEvent.of(consumerName, eventId));
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateInboxEventException(consumerName, eventId, e);
        }
    }
}
