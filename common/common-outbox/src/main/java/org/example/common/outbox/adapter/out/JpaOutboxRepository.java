package org.example.common.outbox.adapter.out;

import org.example.common.outbox.domain.OutboxDispatchType;
import org.example.common.outbox.domain.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JpaOutboxRepository extends JpaRepository<JpaOutbox, String> {
    List<JpaOutbox> findByDispatchTypeAndStatusOrderByCreatedAtAsc(
            OutboxDispatchType dispatchType,
            OutboxStatus status,
            Pageable pageable
    );
}
