package org.example.common.inbox.exception;

public abstract class InboxException extends RuntimeException {

    protected InboxException(String message) {
        super(message);
    }

    protected InboxException(String message, Throwable cause) {
        super(message, cause);
    }
}
