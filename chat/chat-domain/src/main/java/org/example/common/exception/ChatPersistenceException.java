package org.example.common.exception;

public class ChatPersistenceException extends InfrastructureException {
    public ChatPersistenceException(String message) {
        super(message);
    }

    public ChatPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
