package org.example.dlq.adapter;

import org.example.dlq.domain.Dlq;
import org.example.dlq.domain.DlqStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DlqRepository extends JpaRepository<Dlq, String> {
    List<Dlq> findByStatusOrderByCreatedAtAsc(DlqStatus status, Pageable pageable);
}
