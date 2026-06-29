package org.example.common.dlq.exception;

import org.example.common.exception.InfrastructureException;

public class DlqException extends InfrastructureException {

    public DlqException(String message) {
        super(message);
    }

    public DlqException(String message, Throwable cause) {
        super(message, cause);
    }
}