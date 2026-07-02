package org.example.chat.exception;

public class TemporaryChatPersistenceException extends ChatPersistenceException {

    public TemporaryChatPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}