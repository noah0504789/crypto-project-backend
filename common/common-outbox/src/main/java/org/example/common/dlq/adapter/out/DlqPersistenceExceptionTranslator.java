package org.example.common.dlq.adapter.out;

import org.example.common.dlq.exception.DlqPersistenceException;
import org.example.common.dlq.exception.TemporaryDlqPersistenceException;
import org.hibernate.exception.JDBCConnectionException;
import org.springframework.dao.TransientDataAccessException;

import java.sql.SQLTransientConnectionException;

public final class DlqPersistenceExceptionTranslator {

    private static final String CONNECTION_NOT_AVAILABLE = "Connection is not available";

    private DlqPersistenceExceptionTranslator() {
    }

    public static DlqPersistenceException translate(String message, Exception e) {
        if (isTemporaryFailure(e)) {
            return new TemporaryDlqPersistenceException(message, e);
        }

        return new DlqPersistenceException(message, e);
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
            if (message != null && message.contains(CONNECTION_NOT_AVAILABLE)) {
                return true;
            }

            cur = cur.getCause();
        }

        return false;
    }
}