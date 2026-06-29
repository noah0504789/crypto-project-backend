package org.example.common.outbox.exception;

import org.example.common.exception.InfrastructureException;

public class OutboxException extends InfrastructureException {

    public OutboxException(String message) {
        super(message);
    }

    public OutboxException(String message, Throwable cause) {
        super(message, cause);
    }
}