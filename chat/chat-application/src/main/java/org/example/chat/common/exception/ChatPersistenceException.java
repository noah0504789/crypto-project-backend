package org.example.chat.common.exception;

import org.example.common.exception.InfrastructureException;

public class ChatPersistenceException extends InfrastructureException {
    public ChatPersistenceException(String message) {
        super(message);
    }

    public ChatPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
