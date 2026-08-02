package org.example.notification.exception;

import org.example.common.exception.InfrastructureException;

public class NotificationCacheException extends InfrastructureException {

    public NotificationCacheException(String message) {
        super(message);
    }

    public NotificationCacheException(String message, Throwable cause) {
        super(message, cause);
    }
}
