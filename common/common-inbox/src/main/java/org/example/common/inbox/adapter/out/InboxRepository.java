package org.example.common.inbox.adapter.out;

import org.example.common.inbox.domain.Inbox;
import org.example.common.inbox.exception.DuplicateInboxException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;

import java.sql.SQLException;

public interface InboxRepository extends JpaRepository<Inbox, String> {

    int MYSQL_DUPLICATE_KEY = 1062;

    default void insertAndFlush(Inbox inbox) {
        try {
            saveAndFlush(inbox);
        } catch (DataIntegrityViolationException e) {
            if (isDuplicateKey(e)) {
                throw new DuplicateInboxException(inbox.getConsumerName(), inbox.getEventId(), e);
            }
            throw e;
        }
    }

    private boolean isDuplicateKey(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException
                    && sqlException.getErrorCode() == MYSQL_DUPLICATE_KEY
                    && "23000".equals(sqlException.getSQLState())) {
                return true;
            }
        }
        return false;
    }
}
