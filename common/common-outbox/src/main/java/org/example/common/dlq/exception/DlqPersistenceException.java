package org.example.common.dlq.exception;

public class DlqPersistenceException extends DlqException {

    public DlqPersistenceException(String message) {
        super(message);
    }

    public DlqPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}