package org.example.common.dlq.exception;

public class TemporaryDlqPersistenceException extends DlqPersistenceException {

    public TemporaryDlqPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}