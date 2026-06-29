package org.example.chat.common.exception;

public class TemporaryChatPersistenceException extends ChatPersistenceException {

    public TemporaryChatPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}