package org.example.common.dlq.adapter.out;

import org.example.common.dlq.domain.DlqStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JpaDlqRepository extends JpaRepository<JpaDlq, String> {
    List<JpaDlq> findByStatusOrderByCreatedAtAsc(DlqStatus status, Pageable pageable);
}
