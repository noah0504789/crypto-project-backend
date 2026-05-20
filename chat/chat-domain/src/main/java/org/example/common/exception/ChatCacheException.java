package org.example.common.exception;

public class ChatCacheException extends InfrastructureException {
    public ChatCacheException(String message) {
        super(message);
    }

    public ChatCacheException(String message, Throwable cause) {
        super(message, cause);
    }
}
