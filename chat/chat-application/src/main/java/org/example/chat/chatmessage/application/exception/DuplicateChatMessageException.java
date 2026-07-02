package org.example.chat.chatmessage.application.exception;

import org.example.chat.exception.ChatPersistenceException;

public class DuplicateChatMessageException extends ChatPersistenceException {

    public DuplicateChatMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}