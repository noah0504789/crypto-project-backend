package org.example.notification.application.exception;

import org.example.notification.exception.NotificationException;

public class NotificationPersistException extends NotificationException {

    public NotificationPersistException(String message, Throwable cause) {
        super(message, cause);
    }
}