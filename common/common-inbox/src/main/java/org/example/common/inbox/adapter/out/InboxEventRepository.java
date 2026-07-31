package org.example.common.inbox.adapter.out;

import org.example.common.inbox.domain.InboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InboxEventRepository extends JpaRepository<InboxEvent, String> {
}
