package org.example.common.outbox.adapter;

import org.example.common.outbox.domain.Outbox;
import org.example.common.outbox.domain.OutboxDispatchType;
import org.example.common.outbox.domain.OutboxStatus;
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
