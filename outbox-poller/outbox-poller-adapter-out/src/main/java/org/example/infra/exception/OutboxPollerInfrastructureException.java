package org.example.infra.exception;

import org.example.common.exception.InfrastructureException;

public class OutboxPollerInfrastructureException extends InfrastructureException {
    public OutboxPollerInfrastructureException(String message) {
        super(message);
    }
}
