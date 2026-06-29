package org.example.chat.common.exception;

import org.example.common.exception.InfrastructureException;

public class ChatCacheException extends InfrastructureException {

    public ChatCacheException(String message) {
        super(message);
    }

    public ChatCacheException(String message, Throwable cause) {
        super(message, cause);
    }
}
