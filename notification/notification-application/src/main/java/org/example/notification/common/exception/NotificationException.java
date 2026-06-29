package org.example.notification.common.exception;

import org.example.common.exception.InfrastructureException;

public class NotificationException extends InfrastructureException {

    public NotificationException(String message, Throwable cause) {
        super(message, cause);
    }
}