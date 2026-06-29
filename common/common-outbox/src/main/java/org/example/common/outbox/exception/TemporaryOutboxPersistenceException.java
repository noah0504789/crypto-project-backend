package org.example.common.outbox.exception;

public class TemporaryOutboxPersistenceException extends OutboxPersistenceException {

    public TemporaryOutboxPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}