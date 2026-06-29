package org.example.chat.common.exception;

public class DuplicateChatMessageException extends ChatPersistenceException {

    public DuplicateChatMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}