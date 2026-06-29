package org.example.common.outbox.exception;

public class OutboxPersistenceException extends OutboxException {

    public OutboxPersistenceException(String message) {
        super(message);
    }

    public OutboxPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}