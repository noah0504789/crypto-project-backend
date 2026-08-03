package org.example.common.inbox.adapter.out;

import org.example.common.inbox.domain.Inbox;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InboxRepository extends JpaRepository<Inbox, String> {
}
