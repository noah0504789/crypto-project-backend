package org.example.common.dlq.adapter;

import org.example.common.dlq.domain.Dlq;
import org.example.common.dlq.domain.DlqStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DlqRepository extends JpaRepository<Dlq, String> {
    List<Dlq> findByStatusOrderByCreatedAtAsc(DlqStatus status, Pageable pageable);
}
