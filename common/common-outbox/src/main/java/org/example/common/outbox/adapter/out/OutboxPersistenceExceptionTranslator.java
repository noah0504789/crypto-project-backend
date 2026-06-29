package org.example.common.outbox.adapter.out;

import org.example.common.outbox.exception.OutboxPersistenceException;
import org.example.common.outbox.exception.TemporaryOutboxPersistenceException;
import org.hibernate.exception.JDBCConnectionException;
import org.springframework.dao.TransientDataAccessException;

import java.sql.SQLTransientConnectionException;

public final class OutboxPersistenceExceptionTranslator {

    private OutboxPersistenceExceptionTranslator() {
    }

    public static OutboxPersistenceException translate(String message, Exception e) {
        if (isTemporaryFailure(e)) {
            return new TemporaryOutboxPersistenceException(message, e);
        }

        return new OutboxPersistenceException(message, e);
    }

    private static boolean isTemporaryFailure(Throwable t) {
        Throwable cur = t;

        while (cur != null) {
            if (cur instanceof TransientDataAccessException) {
                return true;
            }

            if (cur instanceof SQLTransientConnectionException) {
                return true;
            }

            if (cur instanceof JDBCConnectionException) {
                return true;
            }

            String message = cur.getMessage();
            if (message != null && message.contains("Connection is not available")) {
                return true;
            }

            cur = cur.getCause();
        }

        return false;
    }
}