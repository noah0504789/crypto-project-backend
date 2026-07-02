package org.example.chat.exception;

import org.example.common.exception.InfrastructureException;

public class ChatPersistenceException extends InfrastructureException {
    public ChatPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
