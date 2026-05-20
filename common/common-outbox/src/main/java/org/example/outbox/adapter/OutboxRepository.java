package org.example.outbox.adapter;

import org.example.outbox.domain.Outbox;
import org.example.outbox.domain.OutboxDispatchType;
import org.example.outbox.domain.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxRepository extends JpaRepository<Outbox, String> {
    List<Outbox> findByDispatchTypeAndStatusOrderByCreatedAtAsc(
            OutboxDispatchType dispatchType,
            OutboxStatus status,
            Pageable pageable
    );
}
